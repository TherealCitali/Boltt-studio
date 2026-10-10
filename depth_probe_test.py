"""Independent Python encoder reference for the EXPERIMENTAL extra-image probe.

No firmware support is implied. Legacy moyface.parse_face overwrites repeated image
fields, so this test walks element descriptors independently instead of using it.
"""
from pathlib import Path
import struct
import sys
from PIL import Image, ImageDraw
from moyface import encode_image, decode_image

ROOT = Path(__file__).resolve().parent
FIXTURE = ROOT / 'app/src/test/resources/depth_probe.bin'


def reference():
    bg = Image.new('RGBA', (240, 296), (16, 32, 49, 255))
    rects = [(6, 0, 36, 6), (36, 6, 42, 30), (36, 36, 42, 60),
             (6, 60, 36, 66), (0, 36, 6, 60), (0, 6, 6, 30), (6, 30, 36, 36)]
    digits = []
    for active in ['abcdef', 'bc', 'abdeg', 'abcdg', 'bcfg', 'acdfg', 'acdefg', 'abc', 'abcdefg', 'abcdfg']:
        img = Image.new('RGBA', (42, 66))
        draw = ImageDraw.Draw(img)
        for segment in active:
            l, t, r, b = rects[ord(segment) - ord('a')]
            draw.rectangle((l, t, r - 1, b - 1), fill='white')
        digits.append(img)
    full = Image.new('RGBA', (240, 296))
    for y in range(139, 157):
        for x in range(12, 228):
            if 72 <= x <= 75:
                continue
            edge = min(y - 139, 156 - y)
            full.putpixel((x, y), (255, 0, 255, 64 if edge < 2 else 128 if edge < 4 else 255))
    bounds = full.getbbox()
    assert bounds == (12, 139, 228, 157)
    fg = full.crop(bounds)
    positions = [(19, 115), (69, 115), (129, 115), (179, 115)]
    simulated = bg.copy()
    for digit, xy in zip([1, 0, 0, 9], positions):
        simulated.alpha_composite(digits[digit], xy)
    simulated.alpha_composite(fg, bounds[:2])
    preview = Image.new('RGBA', (140, 163))
    for y in range(163):
        for x in range(140):
            preview.putpixel((x, y), simulated.getpixel((x * 240 // 140, y * 296 // 163)))
    # 34-byte TimeNum followed by a 14-byte image, then the list terminator.
    header_size = 16 + 2 + 83 + 10 + 14 + 34 + 14 + 2
    offset = (header_size + 3) & ~3
    blobs = bytearray()
    def add(image):
        nonlocal offset
        start = offset
        blob = encode_image(image)
        offset += len(blob)
        blobs.extend(blob)
        return struct.pack('<IHH', start, *image.size)
    dash = add(Image.new('RGBA', (1, 1)))
    bg_desc = add(bg)
    digit_descs = [add(d) for d in digits]
    prev_desc = add(preview)
    fg_desc = add(fg)
    result = bytearray(struct.pack('<HH', 0x23, 0xffff) + prev_desc + struct.pack('<HH', 16, 101))
    result += b'\x01\x01\x00' + b''.join(digit_descs) + b'\x01\x01'
    result += b'\x01\x23' + dash
    result += b'\x01\x00' + struct.pack('<HH', 0, 0) + bg_desc
    result += b'\x01\x02' + b'\0' * 4 + b''.join(struct.pack('<HH', *xy) for xy in positions) + b'\0' * 12
    result += b'\x01\x00' + struct.pack('<HH', *bounds[:2]) + fg_desc
    result += b'\0\0'
    result += b'\0' * (-len(result) % 4)
    assert len(result) == (header_size + 3) & ~3
    return bytes(result + blobs)


def check(data):
    o = struct.unpack_from('<H', data, 14)[0]
    types, images = [], []
    while data[o] == 1:
        t = data[o + 1]
        types.append(t)
        if t == 0x23:
            o += 10
        elif t == 0:
            x, y, offset, w, h = struct.unpack_from('<HHIHH', data, o + 2)
            assert x + w <= 240 and y + h <= 296
            images.append(((x, y), decode_image(data, offset, w, h)))
            o += 14
        elif t == 2:
            assert data[o + 2:o + 6] == b'\0' * 4  # four live positions, set zero
            o += 34
        else:
            raise AssertionError(f'Unexpected element {t}')
    assert data[o:o + 2] == b'\0\0'
    assert types == [0x23, 0, 2, 0]
    assert len(images) == 2 and images[0][0] == (0, 0)
    assert images[1][0] == (12, 139) and images[1][1].size == (216, 18)
    assert set(images[1][1].getchannel('A').getdata()) == {0, 64, 128, 255}


if __name__ == '__main__':
    expected = reference()
    check(expected)
    if '--write-fixture' in sys.argv:
        FIXTURE.write_bytes(expected)
    actual = FIXTURE.read_bytes()
    assert actual == expected, 'Depth fixture differs from independent Python encoding'
    check(actual)
    print(f'Depth probe: {len(actual)} bytes; background → live TimeNum → foreground verified structurally. Firmware support UNKNOWN.')
