from pathlib import Path
from PIL import Image

source = Path('/home/ubuntu/work_reader/ZERAVRIX-PC-0.5.0/desktop-app/src/main/resources/branding/logo-square.png')
target = Path('/home/ubuntu/work_reader/ZERAVRIX-PC-0.5.0/tools/zeravrix.ico')
image = Image.open(source).convert('RGBA')
image.save(target, format='ICO', sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
print(target)
