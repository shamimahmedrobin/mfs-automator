#!/usr/bin/env python3
"""Bump versionCode (+1) and versionName (patch/minor +1) in app/build.gradle.kts.

Prints the new versionName so the workflow can tag the release.
"""
import re
import sys

PATH = "app/build.gradle.kts"

with open(PATH) as f:
    s = f.read()

m = re.search(r"versionCode\s*=\s*(\d+)", s)
if not m:
    sys.exit("versionCode not found")
code = int(m.group(1)) + 1
s = s.replace(m.group(0), f"versionCode = {code}", 1)

v = re.search(r'versionName\s*=\s*"(\d+)\.(\d+)\.(\d+)"', s)
if v:
    major, minor, patch = map(int, v.groups())
    new = f"{major}.{minor}.{patch + 1}"
else:
    v2 = re.search(r'versionName\s*=\s*"(\d+)\.(\d+)"', s)
    if not v2:
        sys.exit("versionName not found")
    major, minor = map(int, v2.groups())
    minor += 1
    if minor >= 10:
        # Roll over: 2.9 -> 3.0 (never 2.10)
        major += 1
        minor = 0
    new = f"{major}.{minor}"
s = s.replace(v.group(0) if v else v2.group(0), f'versionName = "{new}"', 1)

with open(PATH, "w") as f:
    f.write(s)

print(new)
