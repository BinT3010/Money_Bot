"""Đóng gói source + APK, loại runtime/database/cache/mật khẩu. Chạy từ mọi cwd."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib
import json

root = Path(__file__).resolve().parents[1]
output = root.parent / "MONEY_BOT_SOURCE.zip"
skip = {".tools", ".venv", "__pycache__", ".pytest_cache", ".gradle", "build", "data", ".git"}
files = []
for path in root.rglob("*"):
    rel = path.relative_to(root)
    if not path.is_file() or any(part in skip for part in rel.parts):
        continue
    if path.name in {"local.properties", ".env"} or path.suffix in {".pyc", ".log", ".sqlite3"} or ".sqlite3-" in path.name:
        continue
    files.append(path)
with ZipFile(output, "w", ZIP_DEFLATED) as archive:
    for path in sorted(files):
        archive.write(path, "MONEY_BOT/" + path.relative_to(root).as_posix())
with ZipFile(output) as archive:
    assert archive.testzip() is None, "ZIP integrity check failed"
print(json.dumps({"file": str(output), "files": len(files), "bytes": output.stat().st_size,
                  "sha256": hashlib.sha256(output.read_bytes()).hexdigest()}, indent=2))
