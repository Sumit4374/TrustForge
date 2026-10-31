#!/usr/bin/env python3
"""Check actual release artifacts; absence/failure is not a passing result."""
from pathlib import Path
import re
import zipfile

root=Path(__file__).resolve().parents[1]
jars=list((root/'server/target').glob('*.jar'))
if len(jars)!=1:raise SystemExit('Build exactly one executable server JAR first.')
with zipfile.ZipFile(jars[0]) as jar:
    names=jar.namelist()
    if not any(name.startswith('BOOT-INF/classes/') for name in names):raise SystemExit('Expected an executable application JAR.')
    if any('testinfra/' in name or 'LiveIT.class' in name or 'transport-zerodep' in name for name in names):raise SystemExit('Test-only inference/infrastructure or vulnerable shaded transport found in release artifact.')
dist=root/'client/dist'
if not (dist/'index.html').is_file():raise SystemExit('Build the frontend first.')
for path in dist.rglob('*'):
    if path.is_file():
        if path.suffix=='.map' or path.name.startswith('.env'):raise SystemExit('Sensitive build metadata exposed.')
        if path.suffix in ['.js','.html'] and re.search(r'Bearer [A-Za-z0-9._-]{24,}',path.read_text()):raise SystemExit('Potential embedded bearer credential requires review.')
print('PASS: executable JAR excludes test-only providers, live tests and shaded zerodep; frontend has no source maps/env files or embedded bearer credentials.')
