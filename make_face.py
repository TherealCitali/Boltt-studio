"""Make a custom face:  python make_face.py background.jpg font.ttf out.bin [x y] [hexcolor]
Clock is 'HH over MM' by default; x y = top-left of the hour digits."""
import sys
from PIL import Image, ImageDraw, ImageFont, ImageOps
from moyface import build_face, parse_face, W, H, DIGIT_W, DIGIT_H

def render_digits(font_path, color, w=DIGIT_W, h=DIGIT_H):
    font = ImageFont.truetype(font_path, int(h * 1.15)); out = []
    for n in range(10):
        big = Image.new('RGBA', (w * 2, h * 2), (0, 0, 0, 0)); d = ImageDraw.Draw(big)
        d.text((w, h), str(n), font=font, fill=color, anchor='mm', stroke_width=2, stroke_fill=(0, 0, 0, 160))
        bb = big.getbbox(); g = big.crop(bb); g.thumbnail((w, h), Image.LANCZOS)
        cell = Image.new('RGBA', (w, h), (0, 0, 0, 0)); cell.alpha_composite(g, ((w - g.width) // 2, (h - g.height) // 2))
        out.append(cell)
    return out

def make(bg_path, font_path, out_path, x=24, y=140, color='#FFFFFF', layout='stacked'):
    bg = ImageOps.fit(Image.open(bg_path).convert('RGBA'), (W, H), Image.LANCZOS)
    rgb = tuple(int(color.lstrip('#')[i:i + 2], 16) for i in (0, 2, 4))
    digits = render_digits(font_path, rgb + (255,))
    if layout == 'stacked':
        xy = [(x, y), (x + 50, y), (x, y + 72), (x + 50, y + 72)]
    else:  # one line HH MM
        xy = [(x, y), (x + 44, y), (x + 100, y), (x + 144, y)]
    preview = bg.copy()
    for i, n in enumerate([1, 0, 0, 9]): preview.alpha_composite(digits[n], xy[i])
    data = build_face(bg, [digits], xy, time_sets=(0, 0, 0, 0), preview=preview.resize((140, 163)))
    open(out_path, 'wb').write(data); preview.convert('RGB').save(out_path.rsplit('.', 1)[0] + '_preview.png')
    parse_face(data)  # sanity check
    print(f'wrote {out_path} ({len(data)} bytes)')

if __name__ == '__main__':
    a = sys.argv[1:]
    make(a[0], a[1], a[2], *(int(v) for v in a[3:5]), *(a[5:6] or []))
