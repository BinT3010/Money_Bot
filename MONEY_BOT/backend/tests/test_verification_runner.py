"""Unattended integration must not accept stale or changing-revision evidence."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
from unittest.mock import patch

import pytest

SPEC = importlib.util.spec_from_file_location("verification_runner", Path(__file__).resolve().parents[2] / "scripts" / "verify.py")
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)


@pytest.mark.parametrize("final_revision,final_dirty", [("changed", ""), ("original", " M app.py")])
def test_changed_checkout_is_never_integration_eligible(tmp_path, final_revision, final_dirty):
    out = tmp_path / "evidence"
    with patch.object(sys, "argv", ["verify.py", "--backend-only", "--out", str(out)]), patch.object(runner.subprocess, "check_output", side_effect=["original", "", final_revision, final_dirty]), patch.object(runner.subprocess, "run", return_value=subprocess.CompletedProcess([], 0)):
        assert runner.main() == 0
    result = json.loads((out / "verification.json").read_text())
    assert result["final_revision"] == final_revision
    assert result["final_dirty"] == bool(final_dirty)
    assert not result["integration_eligible"]


def test_old_green_evidence_is_invalidated_before_commands_run(tmp_path):
    out = tmp_path / "evidence"
    out.mkdir()
    manifest = out / "verification.json"
    manifest.write_text('{"integration_eligible":true,"status":"finished"}')
    def timeout(*args, **kwargs):
        in_progress = json.loads(manifest.read_text())
        assert in_progress["status"] == "running"
        assert not in_progress["integration_eligible"]
        assert kwargs["timeout"] == 1200
        raise subprocess.TimeoutExpired(args[0], 1200)
    with patch.object(sys, "argv", ["verify.py", "--backend-only", "--out", str(out)]), patch.object(runner.subprocess, "check_output", side_effect=["original", "", "original", ""]), patch.object(runner.subprocess, "run", side_effect=timeout):
        assert runner.main() == 1
    result = json.loads(manifest.read_text())
    assert not result["passed"]
    assert not result["integration_eligible"]
