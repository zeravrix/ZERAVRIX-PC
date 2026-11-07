from __future__ import annotations

import hashlib
import os
import shutil
import subprocess
from pathlib import Path

ROOT = Path('/home/ubuntu/work_reader/ZERAVRIX-PC-0.5.0')
PORTABLE = ROOT.parent / 'deliverables' / 'portable' / 'ZERAVRIX-Store-1.5.0'
STORE = ROOT / 'store-package'
OUT = ROOT.parent / 'deliverables' / 'zeravrix-store-1.5.0'
STAGE = OUT / 'msix-staging'
PACKAGE = OUT / 'ZERAVRIX-Store-1.5.0-x64.msix'
UPLOAD = OUT / 'ZERAVRIX-Store-1.5.0-x64.msixupload'
SDK_PACKAGER = Path(os.environ.get('MAKEMSIX_PATH', '/home/ubuntu/work_reader/msix-packaging/.vs/bin/makemsix'))

if not PORTABLE.is_dir():
    raise SystemExit(f'Missing portable payload: {PORTABLE}')

if STAGE.exists():
    shutil.rmtree(STAGE)
STAGE.mkdir(parents=True)

for item in PORTABLE.iterdir():
    if item.name in {'EXTENSION-STORES.md', 'TRANSLATION-SETUP.md', 'Assets'}:
        continue
    target = STAGE / item.name
    if item.is_dir():
        shutil.copytree(item, target)
    else:
        shutil.copy2(item, target)

shutil.copy2(STORE / 'AppxManifest.xml', STAGE / 'AppxManifest.xml')
shutil.copytree(STORE / 'Assets', STAGE / 'Assets')
shutil.copy2(ROOT / 'installer' / 'LICENSE.txt', STAGE / 'LICENSE.txt')

for old in (STAGE / 'AppxBlockMap.xml', STAGE / 'AppxSignature.p7x'):
    old.unlink(missing_ok=True)
for old in (PACKAGE, UPLOAD, Path(str(PACKAGE) + '.sha256'), Path(str(UPLOAD) + '.sha256')):
    old.unlink(missing_ok=True)

inventory = OUT / 'payload-inventory.txt'
inventory.write_text('\n'.join(p.relative_to(STAGE).as_posix() for p in sorted(STAGE.rglob('*')) if p.is_file()) + '\n', encoding='utf-8')
print(f'Created staging directory: {STAGE}')

if not SDK_PACKAGER.is_file():
    print(f'MSIX SDK packager not found at {SDK_PACKAGER}; no MSIX was created.')
    print('Install/use makemsix or run store/build-msix-on-windows.ps1 on Windows.')
    raise SystemExit(0)

subprocess.run([str(SDK_PACKAGER), 'pack', '-d', str(STAGE), '-p', str(PACKAGE)], check=True)
subprocess.run(['zip', '-X', '-q', str(UPLOAD), PACKAGE.name], cwd=OUT, check=True)
for artifact in (PACKAGE, UPLOAD):
    digest = hashlib.sha256(artifact.read_bytes()).hexdigest()
    artifact.with_name(artifact.name + '.sha256').write_text(f'{digest}  {artifact.name}\n', encoding='utf-8')
    print(f'Created {artifact}: {digest}')
