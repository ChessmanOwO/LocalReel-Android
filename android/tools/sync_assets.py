"""Sync edited project sources, preserving the pinned vendored dependencies."""
from pathlib import Path
import zipfile

root = Path(__file__).resolve().parents[1]
assets = root / "app/src/main/assets"
engine = assets / "engine.zip"
with zipfile.ZipFile(engine) as old:
    dependencies = [(i.filename, old.read(i)) for i in old.infolist()
                    if i.filename.startswith(("yt_dlp/", "yt_dlp_ejs/"))]
if not dependencies:
    raise SystemExit("Missing vendored dependencies; restore the supplied engine.zip.")
temporary = engine.with_suffix(".zip.tmp")
with zipfile.ZipFile(temporary, "w", zipfile.ZIP_DEFLATED) as new:
    for name, data in dependencies:
        new.writestr(name, data)
    for source in sorted((root / "engine").rglob("*.py")):
        if "__pycache__" not in source.parts:
            new.write(source, source.relative_to(root / "engine").as_posix())
temporary.replace(engine)
base = (assets / "index-base.html").read_text(encoding="utf-8")
script = (assets / "app.js").read_text(encoding="utf-8")
needle = '<script src="app.js"></script>'
if needle not in base:
    raise SystemExit("Expected app.js script reference in index-base.html")
(assets / "index.html").write_text(base.replace(needle, "<script>" + script + "</script>"), encoding="utf-8")
print("Updated engine.zip and index.html. Build the APK again.")
