"""Development transcripts must never be reported as held-out OCR acceptance."""
import importlib.util
import json
from pathlib import Path
import pytest

CAPTURE = Path(__file__).resolve().parents[2] / 'evaluation' / 'capture'
spec = importlib.util.spec_from_file_location('capture_measurement', CAPTURE / 'measure.py')
measurement = importlib.util.module_from_spec(spec)
spec.loader.exec_module(measurement)


def test_transcripts_are_scored_but_never_qualify_as_ocr(tmp_path):
    report = measurement.measure(CAPTURE / 'data' / 'dev.json', tmp_path / 'report.json')
    assert report['metrics']['receipt']['amount_vnd']['total'] == 6
    assert report['full_ocr_receipts'] == 0
    assert not report['sample_requirements_met']
    assert not report['report_acceptance_met']
    assert report['environment']['device_ui_measured'] is False


def test_transcripts_cannot_be_relabelled_heldout(tmp_path):
    data = json.loads((CAPTURE / 'data' / 'dev.json').read_text())
    data['split'] = 'heldout'
    case = data['cases'][0]
    case['file'] = str(CAPTURE / 'data' / case['file'])
    data['cases'] = [case]
    manifest = tmp_path / 'invalid.json'
    manifest.write_text(json.dumps(data))
    with pytest.raises(ValueError, match='development cases'):
        measurement.measure(manifest, tmp_path / 'report.json')
    assert not (tmp_path / 'report.json').exists()
