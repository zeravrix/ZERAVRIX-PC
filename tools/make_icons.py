from pathlib import Path
from PIL import Image

root = Path('/home/ubuntu/work_reader/ZERAVRIX-PC-0.5.0')
square = Image.open(root / 'desktop-app/src/main/resources/branding/logo-square.png').convert('RGBA')
# Keep the legacy resource path as a compatibility alias to the supplied square logo.
square.save(root / 'desktop-app/src/main/resources/logo.png', optimize=True)
# Installer wizard image: opaque RGB BMP, matching the square supplied logo.
bmp = square.convert('RGB').resize((512, 512), Image.Resampling.LANCZOS)
bmp.save(root / 'installer/logo.bmp')
# Multi-resolution Windows icon used by installer shortcuts.
icon = square.resize((256, 256), Image.Resampling.LANCZOS)
icon.save(root / 'installer/logo.ico', sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
