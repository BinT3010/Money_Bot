"""Capture regressions: confirmation, merchant provenance and safe receipt parsing."""
from datetime import date
from uuid import uuid4
from unittest.mock import patch

from fastapi.testclient import TestClient
import pytest
from moneybot.app import create_app
from moneybot.ocr import receipt_fields

TODAY = date(2026, 11, 12)

@pytest.fixture
def client(tmp_path):
    with TestClient(create_app(tmp_path, lambda: TODAY)) as c:
        auth = c.post('/api/auth/register', json={'email': 'capture@example.test', 'password': 'Password123'}).json()
        c.headers['Authorization'] = 'Bearer ' + auth['token']
        yield c

def action(c, body, key=None):
    r = c.post('/api/chat/android', json=body, headers={'Idempotency-Key': key or uuid4().hex})
    assert r.status_code == 200, r.text
    return r.json()

def edit(c, state, **fields):
    d = state['draft']
    return action(c, {'action': 'edit', 'draft_id': d['id'], 'version': d['version'], 'fields': fields})

def confirm(c, state, key=None):
    d = state['draft']
    return action(c, {'action': 'confirm', 'draft_id': d['id'], 'version': d['version']}, key)

def rows(c):
    return c.get('/api/transactions?month=2026-11').json()

def test_text_merchant_edit_stale_confirm_and_replay(client):
    state = action(client, {'action': 'send', 'text': 'ăn trưa 45k; cửa hàng: Quán Minh'})
    assert state['draft']['fields']['merchant'] == 'Quán Minh'
    assert state['draft']['fields']['description'] == 'ăn trưa'
    assert rows(client) == []
    changed = edit(client, state, merchant='Quán An')
    assert changed['draft']['fields']['merchant'] == 'Quán An'
    assert confirm(client, state)['draft']
    assert rows(client) == []
    key = uuid4().hex
    first = confirm(client, changed, key)
    assert confirm(client, changed, key) == first
    assert len(rows(client)) == 1 and rows(client)[0]['merchant'] == 'Quán An'

def test_merchant_only_followup_keeps_description_and_category(client):
    state = action(client, {'action': 'send', 'text': 'ăn trưa 45k'})
    changed = action(client, {'action': 'send', 'text': 'cửa hàng: Nhà sách An'})
    fields = changed['draft']['fields']
    assert fields['merchant'] == 'Nhà sách An'
    assert fields['description'] == state['draft']['fields']['description']
    assert fields['category_id'] == 'food' and rows(client) == []

def test_invalid_merchant_blocks_draft_and_can_be_cleared(client):
    state = action(client, {'action': 'send', 'text': 'ăn trưa 45k'})
    state = edit(client, state, merchant='a' * 121)
    assert not state['can_confirm'] and 'merchant' in state['errors']
    assert rows(client) == []
    state = edit(client, state, merchant='')
    assert state['can_confirm']
    confirm(client, state)
    assert rows(client)[0]['merchant'] is None

def test_receipt_missing_date_requires_edit_before_confirm(client):
    text = 'Store: CAFE MINH\nRECEIPT\nca phe\nTOTAL 45,000'
    with patch('moneybot.routes.chat.extract_text', return_value=text):
        r = client.post('/api/chat/android/receipt', files={'file': ('r.png', b'fixture')}, headers={'Idempotency-Key': 'upload-1'})
    assert r.status_code == 200
    state = r.json()
    assert state['draft']['fields']['merchant'] == 'CAFE MINH'
    assert 'date' not in state['draft']['fields']
    assert not state['can_confirm'] and 'date' in state['errors'] and rows(client) == []
    with patch('moneybot.routes.chat.extract_text', side_effect=AssertionError('cached upload must not rerun OCR')):
        replay = client.post('/api/chat/android/receipt', files={'file': ('r.png', b'fixture')}, headers={'Idempotency-Key': 'upload-1'})
    assert replay.json() == state
    changed = edit(client, state, date='2026-11-11', merchant='CAFE AN', description='Cà phê', category_id='food')
    assert changed['can_confirm'] and rows(client) == []
    confirm(client, changed)
    assert rows(client)[0]['date'] == '2026-11-11' and rows(client)[0]['merchant'] == 'CAFE AN'

def test_rejected_receipt_keeps_existing_draft_then_cancel(client):
    state = action(client, {'action': 'send', 'text': 'ăn trưa 45k'})
    with patch('moneybot.routes.chat.extract_text', return_value='TOTAL 50,000\n12/11/2026'):
        r = client.post('/api/chat/android/receipt', files={'file': ('r.png', b'fixture')}, headers={'Idempotency-Key': uuid4().hex})
    assert r.status_code == 400
    assert client.get('/api/chat/android').json()['draft']['id'] == state['draft']['id']
    d = state['draft']
    action(client, {'action': 'cancel', 'draft_id': d['id'], 'version': d['version']})
    assert rows(client) == []

@pytest.mark.parametrize('text', ['Store: CAFE MINH\nRECEIPT\nTOTAL 45,000\n12/11/2026', 'CAFE MINH\nRECEIPT\nTOTAL 45,000\n12/11/2026'])
def test_merchant_is_not_category_evidence(text):
    fields, issues = receipt_fields(text, TODAY)
    assert fields['merchant'] == 'CAFE MINH'
    assert 'category_id' not in fields and not issues

@pytest.mark.parametrize('text', ['TOTAL (45,000)\n12/11/2026', 'TOTAL 45,000-\n12/11/2026', 'TOTAL -45,000\n12/11/2026', 'TOTAL −45,000\n12/11/2026', 'TOTAL 45,000\nTOTAL -45,000\n12/11/2026', 'TOTAL USD 45.00\n12/11/2026', 'TOTAL 45,000\nTOTAL 55,000\n12/11/2026'])
def test_unsafe_totals_require_correction(text):
    fields, issues = receipt_fields(text, TODAY)
    assert 'amount_vnd' not in fields and 'amount_vnd' in issues

@pytest.mark.parametrize('date_text', ['', '31/02/2026', '12/11/2026\n11/11/2026'])
def test_unresolved_dates_are_not_replaced_with_today(date_text):
    fields, issues = receipt_fields('TOTAL 45,000\n' + date_text, TODAY)
    assert 'date' not in fields and 'date' in issues

def test_iso_receipt_date_and_ambiguous_merchant():
    fields, issues = receipt_fields('Store: An\nMerchant: Binh\nTOTAL 45,000\n2026-11-11', TODAY)
    assert fields['date'] == '2026-11-11'
    assert 'merchant' not in fields and 'merchant' in issues

@pytest.mark.parametrize('merchant', ['thời tiết', 'đã thanh toán', 'hàng tháng', 'tổng chi tháng 11/2026'])
def test_merchant_metadata_cannot_control_conversation_intent(client, merchant):
    state = action(client, {'action': 'send', 'text': f'ăn trưa 45k; merchant: {merchant}'})
    assert state['draft']['intent'] == 'record_expense'
    assert state['draft']['fields']['merchant'] == merchant
    assert state['draft']['fields']['category_id'] == 'food'
    assert rows(client) == []
