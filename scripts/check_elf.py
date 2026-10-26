"""Verify every packaged ARM64 library's ELF LOAD alignment after assembleDebug."""
from pathlib import Path
import subprocess, tempfile, zipfile, sys
apk=Path(sys.argv[1] if len(sys.argv)>1 else "app/build/outputs/apk/debug/app-debug.apk")
count=0
with tempfile.TemporaryDirectory() as temp, zipfile.ZipFile(apk) as archive:
    for name in archive.namelist():
        if not name.startswith("lib/") or not name.endswith(".so"):continue
        assert name.startswith("lib/arm64-v8a/"), "Unexpected ABI: "+name
        out=Path(temp)/Path(name).name;out.write_bytes(archive.read(name))
        output=subprocess.check_output(["readelf","-lW",str(out)],text=True)
        loads=[line for line in output.splitlines() if line.strip().startswith("LOAD")]
        assert loads,"Missing ELF load segments: "+name
        for line in loads:
            alignment=int(line.split()[-1],16)
            assert alignment>=16384,"Not 16KB aligned: "+name
        count+=1
assert count>0,"No native libraries packaged"
print("PASS:",count,"ARM64 libraries have >=16KB ELF LOAD alignment")
