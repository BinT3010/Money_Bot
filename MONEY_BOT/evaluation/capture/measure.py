"""Measure labeled capture cases through the API; never change labels from output."""
import argparse
from collections import Counter
from datetime import date
import json
import platform
from importlib.metadata import version
from pathlib import Path
import subprocess
import sys
import tempfile
import time
from unittest.mock import patch
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'backend'))
from fastapi.testclient import TestClient
from moneybot.app import create_app


def normalized(value):
    return ' '.join(str(value).casefold().split()) if value is not None else None


def measure(manifest, output):
    data = json.loads(manifest.read_text(encoding='utf-8'))
    today = date.fromisoformat(data['today'])
    if data['split'] not in {'dev', 'heldout'} or not data['cases']:
        raise ValueError('Use a nonempty, explicitly identified dev or heldout manifest.')
    ids = [case['id'] for case in data['cases']]
    if len(set(ids)) != len(ids):
        raise ValueError('Case IDs must be unique.')
    for case in data['cases']:
        if case['kind'] not in {'receipt', 'sentence'} or not {'amount_vnd', 'date', 'category_id'} <= case['expected'].keys():
            raise ValueError('Each case needs its source kind and predefined amount/date/category labels.')
    results = []
    with tempfile.TemporaryDirectory(prefix='capture-measure-') as folder, TestClient(create_app(folder, lambda: today)) as client:
        auth = client.post('/api/auth/register', json={'email': 'capture@benchmark.test', 'password': 'Password123'}).json()
        client.headers['Authorization'] = 'Bearer ' + auth['token']
        for index, case in enumerate(data['cases']):
            started = time.perf_counter()
            url = f'/api/chat/capture-{index}'
            headers = {'Idempotency-Key': uuid4().hex}
            full_ocr = False
            if case['kind'] == 'sentence':
                response = client.post(url, json={'action': 'send', 'text': case['text']}, headers=headers)
            else:
                path = manifest.parent / case['file']
                if path.suffix == '.txt':
                    if data['split'] != 'dev':
                        raise ValueError('Transcript-only receipt fixtures are development cases, not held-out OCR evidence.')
                    with patch('moneybot.routes.chat.extract_text', return_value=path.read_text(encoding='utf-8')):
                        response = client.post(url + '/receipt', files={'file': (path.name, b'development transcript')}, headers=headers)
                else:
                    full_ocr = True
                    response = client.post(url + '/receipt', files={'file': (path.name, path.read_bytes())}, headers=headers)
            elapsed = (time.perf_counter() - started) * 1000
            body = response.json()
            actual = (body.get('draft') or {}).get('fields', {})
            comparisons = {key: response.status_code == 200 and (normalized(actual.get(key)) == normalized(value) if key == 'merchant' else actual.get(key) == value) for key, value in case['expected'].items()}
            results.append({'id': case['id'], 'kind': case['kind'], 'full_ocr': full_ocr,
                            'expected': case['expected'], 'actual': actual, 'correct': comparisons,
                            'status': response.status_code, 'errors': body.get('errors', body),
                            'elapsed_ms': round(elapsed, 2), 'within_10s': response.status_code == 200 and elapsed <= 10000})
    metrics = {}
    for kind in ['receipt', 'sentence']:
        selected = [row for row in results if row['kind'] == kind]
        metrics[kind] = {}
        for key in ['amount_vnd', 'date', 'merchant', 'category_id']:
            scored = [row for row in selected if key in row['expected'] and (key != 'merchant' or row['expected'][key] not in (None, ''))]
            metrics[kind][key] = {'correct': sum(row['correct'][key] for row in scored), 'total': len(scored)}
    confusion = {kind: {expected: dict(Counter(str(row['actual'].get('category_id', 'missing')) for row in results if row['kind'] == kind and row['expected']['category_id'] == expected)) for expected in sorted({row['expected']['category_id'] for row in results if row['kind'] == kind})} for kind in ['receipt', 'sentence']}
    receipt_rows = [r for r in results if r['kind'] == 'receipt' and r['full_ocr']]
    sentence_rows = [r for r in results if r['kind'] == 'sentence']
    categories = ['food', 'shopping', 'transport', 'utilities', 'entertainment', 'other']
    coverage = {kind: Counter(r['expected'].get('category_id') for r in rows) for kind, rows in [('receipt', receipt_rows), ('sentence', sentence_rows)]}
    sufficient = (data['split'] == 'heldout' and len(receipt_rows) >= 50 and len(sentence_rows) >= 20
                  and all(coverage['receipt'][c] >= 5 and coverage['sentence'][c] >= 2 for c in categories))
    accuracy_met = True
    for kind in metrics.values():
        for field, threshold in [('amount_vnd', .9), ('date', .9), ('merchant', .8), ('category_id', .85)]:
            score = kind[field]
            # Merchant is scored only on sources which provide it.
            if field == 'merchant' and score['total'] == 0:
                continue
            accuracy_met &= score['total'] > 0 and score['correct'] / score['total'] >= threshold
    latency_met = bool(receipt_rows) and all(r['within_10s'] for r in receipt_rows)
    report = {'revision': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
              'dirty': bool(subprocess.check_output(['git', 'status', '--porcelain'], cwd=ROOT, text=True).strip()),
              'source': data['source'], 'split': data['split'], 'today': data['today'],
              'environment': {'python': sys.version, 'platform': platform.platform(), 'machine': platform.machine(),
                              'rapidocr': version('rapidocr-onnxruntime'), 'onnxruntime': version('onnxruntime'),
                              'transport': 'in-process FastAPI TestClient', 'device_ui_measured': False},
              'metrics': metrics, 'category_confusion': confusion, 'full_ocr_receipts': len(receipt_rows), 'sentences': len(sentence_rows),
              'coverage': coverage, 'sample_requirements_met': sufficient,
              'accuracy_thresholds_met_on_this_sample': accuracy_met, 'latency_met_on_this_sample': latency_met,
              'field_benchmark_acceptance_met': sufficient and accuracy_met,
              'report_acceptance_met': False,
              'unmeasured_by_this_tool': ['1.3 correction/confirmation flows', '1.5 network and Android display latency'],
              'limitations': 'Synthetic fixtures only; not representative real-world accuracy. Objective 1.3 is covered by API/UI regressions, not this field benchmark. In-process API latency excludes Android rendering and network transport.',
              'cases': results}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({k: report[k] for k in ['metrics', 'full_ocr_receipts', 'sentences', 'sample_requirements_met', 'report_acceptance_met']}, ensure_ascii=False, indent=2))
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('manifest', type=Path)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--require-field-targets', action='store_true')
    args = parser.parse_args()
    report = measure(args.manifest.resolve(), args.out.resolve())
    raise SystemExit(1 if args.require_field_targets and not report['field_benchmark_acceptance_met'] else 0)
