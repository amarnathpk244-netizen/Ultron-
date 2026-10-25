#!/usr/bin/env python3
"""Host checks exercise production Java policy, XML and build metadata; not Android execution."""
from pathlib import Path
import ast, json, subprocess, tempfile, xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
for file in ROOT.rglob("*.xml"):
    ET.parse(file)
for file in (ROOT/"scripts").glob("*.py"):
    ast.parse(file.read_text())
json.loads((ROOT/"app/src/main/assets/models.lock.json").read_text())
with tempfile.TemporaryDirectory() as build:
    subprocess.run(["java","com.sun.tools.javac.Main","-encoding","UTF-8","-d",build,
        str(ROOT/"app/src/main/java/org/ultron/assistant/ToolPolicy.java"),str(ROOT/"scripts/PolicyTest.java")],check=True)
    subprocess.run(["java","-cp",build,"PolicyTest"],check=True)
manifest=ET.parse(ROOT/"app/src/main/AndroidManifest.xml").getroot()
a="{http://schemas.android.com/apk/res/android}"
perms={e.attrib[a+"name"] for e in manifest.findall("uses-permission")}
assert "android.permission.FOREGROUND_SERVICE_MICROPHONE" in perms
assert not perms.intersection({"android.permission.SEND_SMS","android.permission.CALL_PHONE",
 "android.permission.QUERY_ALL_PACKAGES","android.permission.MANAGE_EXTERNAL_STORAGE"})
assert manifest.find("application").attrib[a+"allowBackup"]=="false"
assert manifest.find("application").attrib[a+"usesCleartextTraffic"]=="false"
for service in manifest.find("application").findall("service"):
    if service.attrib[a+"name"]==".ListeningService":
        assert service.attrib[a+"exported"]=="false"
        assert service.attrib[a+"foregroundServiceType"]=="microphone"
assert not list(manifest.iter("receiver"))
print("PASS: XML parsing, Python syntax, JSON parsing, manifest security invariants")
print("NOT RUN: Kotlin/Compose compilation, Android lint, JNI/NDK linking, APK install, device/audio tests")
