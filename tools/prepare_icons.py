from pathlib import Path
from PIL import Image

source = Path('/home/ubuntu/upload/file_000000009f148246b7d1c07da443624b.png')
root = Path('/home/ubuntu/xdownapp/app/src/main/res')
image = Image.open(source).convert('RGBA')

for density, size in {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192,
}.items():
    folder = root / f'mipmap-{density}'
    folder.mkdir(parents=True, exist_ok=True)
    resized = image.resize((size, size), Image.Resampling.LANCZOS)
    resized.save(folder / 'ic_launcher.png', optimize=True)
    resized.save(folder / 'ic_launcher_round.png', optimize=True)
