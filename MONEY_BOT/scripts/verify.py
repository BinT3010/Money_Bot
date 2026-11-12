"""Run the same isolated verification locally and in CI (Python 3.12, JDK 17)."""
import argparse
from contextlib import closing, nullcontext
import json
import os
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import time
import urllib.request
from uuid import uuid4

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--backend-only", action="store_true", help="Partial check; not eligible for integration")
    args = parser.parse_args()
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=True)
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    dirty = bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True).strip())
    report = {"revision": revision, "dirty": dirty, "full": not args.backend_only,
              "passed": False, "integration_eligible": False, "status": "running",
              "run_id": uuid4().hex, "steps": [], "python": sys.version}
    manifest = out / "verification.json"
    manifest.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    env = os.environ.copy()
    env["PYTHONUTF8"] = "1"

    def run(name, command, cwd):
        print(name, flush=True)
        with (out / (name + ".log")).open("w", encoding="utf-8") as log:
            result = subprocess.run(command, cwd=cwd, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=1200)
        report["steps"].append({"name": name, "command": list(map(str, command)), "exit_code": result.returncode})
        if result.returncode:
            print((out / (name + ".log")).read_text(encoding="utf-8", errors="replace")[-8000:])
            raise RuntimeError(f"{name} failed; see {out}")

    server = None
    temporary = tempfile.TemporaryDirectory(prefix="moneybot-verify-")
    try:
        with nullcontext(temporary.name) as directory:
            env["MONEYBOT_DATA"] = str(Path(directory) / "data")
            run("backend", [sys.executable, "-m", "pytest", "-q", "--junitxml=" + str(out / "backend.xml")], ROOT / "backend")
            run("evaluation", [sys.executable, "evaluation/run_eval.py", "--strict", "--out", str(out / "evaluation")], ROOT)
            if not args.backend_only:
                # Java integration tests intentionally use this fixed loopback port.
                with closing(socket.socket()) as probe:
                    probe.bind(("127.0.0.1", 18080))
                with (out / "server.log").open("w", encoding="utf-8") as log:
                    server = subprocess.Popen([sys.executable, "-m", "uvicorn", "app:app", "--host", "127.0.0.1", "--port", "18080"], cwd=ROOT / "backend", env=env, stdout=log, stderr=subprocess.STDOUT)
                    for _ in range(100):
                        if server.poll() is not None:
                            raise RuntimeError("Test server exited; see server.log")
                        try:
                            with urllib.request.urlopen("http://127.0.0.1:18080/api/health", timeout=1) as response:
                                if json.load(response).get("status") == "ok":
                                    break
                        except OSError:
                            time.sleep(0.1)
                    else:
                        raise RuntimeError("Test server did not become ready")
                    wrapper = ["gradlew.bat"] if os.name == "nt" else ["sh", "gradlew"]
                    run("android", [*wrapper, "testDebugUnitTest", "assembleDebug", "lintDebug", "--rerun-tasks", "--console=plain"], ROOT / "android")
                    server.terminate()
                    server.wait(timeout=10)
                    server = None
            report["passed"] = True
    except (OSError, RuntimeError, subprocess.SubprocessError) as exc:
        report["error"] = str(exc)
        print(str(exc), file=sys.stderr)
    finally:
        if server is not None:
            server.terminate()
            try:
                server.wait(timeout=10)
            except subprocess.TimeoutExpired:
                server.kill()
                server.wait()
        temporary.cleanup()
        report["final_revision"] = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
        report["final_dirty"] = bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=ROOT, text=True).strip())
        report["status"] = "finished"
        report["integration_eligible"] = (report["passed"] and report["full"] and not report["dirty"]
                                          and not report["final_dirty"] and report["final_revision"] == revision)
        (out / "verification.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
