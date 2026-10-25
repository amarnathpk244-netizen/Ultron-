from pathlib import Path
from zipfile import ZipFile,ZIP_DEFLATED
root=Path(__file__).resolve().parents[1]
out=root/"ULTRON-source.zip"
with ZipFile(out,"w",ZIP_DEFLATED) as z:
    for path in sorted(root.rglob("*")):
        if not path.is_file() or path==out:continue
        rel=path.relative_to(root)
        if any(part in {"vendor","build",".gradle",".git",".cxx","__pycache__",".idea"} for part in rel.parts):continue
        if path.suffix in {".jks",".keystore",".apk"} or path.name=="local.properties":continue
        z.write(path,"ULTRON/"+str(rel))
print(out)
