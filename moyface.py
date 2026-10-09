"""Encoder/decoder for the MoYoung 'API 0x23' watch-face format used by the Fire-Boltt Brillia
(MOY-7QI2-2.0.1, 240x296). Layout reverse-engineered from a captured Da Fit upload, with
structure names borrowed from david47k/extrathundertool (GPL-2.0-or-later)."""
import struct
from PIL import Image

W, H = 240, 296
DIGIT_W, DIGIT_H = 42, 66

# ---------- pixel helpers: each pixel is 3 bytes [alpha, RGB565 big-endian] ----------
def rgba_to_px(r, g, b, a):
    v = ((r >> 3) << 11) | ((g >> 2) << 5) | (b >> 3)
    return bytes((a, v >> 8, v & 0xFF))

def px_to_rgba(p):
    a, v = p[0], p[1] << 8 | p[2]
    r, g, b = (v >> 11) & 0x1F, (v >> 5) & 0x3F, v & 0x1F
    return (r << 3 | r >> 2, g << 2 | g >> 4, b << 3 | b >> 2, a)

# ---------- RLE image blob (row table + rows) ----------
def decode_image(buf, off, w, h):
    img = Image.new('RGBA', (w, h)); px = img.load()
    for y in range(h):
        lo, sz = struct.unpack_from('<HH', buf, off + 4 * y)
        start = off + lo + ((sz & 0x1F) << 16); row = buf[start:start + (sz >> 5)]
        i = x = 0
        while i < len(row):
            c = row[i]; i += 1
            if c & 0x80:
                p = px_to_rgba(row[i:i + 3]); i += 3
                for _ in range(c & 0x7F):
                    if x < w: px[x, y] = p
                    x += 1
            else:
                for _ in range(c):
                    if x < w: px[x, y] = px_to_rgba(row[i:i + 3])
                    x += 1; i += 3
    return img

def _encode_row(pix):
    out = bytearray(); lit = []; i = 0; n = len(pix)
    def flush():
        while lit:
            chunk = lit[:127]; del lit[:127]
            out.append(len(chunk)); out.extend(b''.join(chunk))
    while i < n:
        j = i
        while j < n and pix[j] == pix[i] and j - i < 127: j += 1
        if j - i >= 2:
            flush(); out.append(0x80 | (j - i)); out.extend(pix[i]); i = j
        else:
            lit.append(pix[i]); i += 1
    flush(); return bytes(out)

def encode_image(img):
    img = img.convert('RGBA'); w, h = img.size; data = list(img.getdata())
    rows = [_encode_row([rgba_to_px(*data[y * w + x]) for x in range(w)]) for y in range(h)]
    table = bytearray(); body = bytearray(); pos = 4 * h
    for r in rows:
        if len(r) >= 1 << 11: raise ValueError('compressed row too long')
        table += struct.pack('<HH', pos & 0xFFFF, (len(r) << 5) | (pos >> 16))
        body += r; pos += len(r)
    blob = bytes(table + body)
    return blob + b'\0' * (-len(blob) % 4)          # keep 32-bit alignment

# ---------- whole face ----------
def parse_face(buf):
    """Return dict with background, digit sets, clock digit positions/sets, preview."""
    u16 = lambda o: struct.unpack_from('<H', buf, o)[0]
    face = {'api': u16(0), 'unknown': u16(2)}
    poff, pw, ph = struct.unpack_from('<IHH', buf, 4)
    face['preview'] = decode_image(buf, poff, pw, ph)
    d_off, e_off = u16(12), u16(14)
    sets = []; o = d_off + 2
    while o < e_off:
        sets.append([decode_image(buf, *struct.unpack_from('<IHH', buf, o + 1 + 8 * n)) for n in range(10)])
        o += 83
    face['digit_sets'] = sets
    o = e_off; face['elements'] = []
    while buf[o] == 1:
        t = buf[o + 1]
        if t == 0x23:   # dash/colon glyph
            off, w, h = struct.unpack_from('<IHH', buf, o + 2); face['dash'] = decode_image(buf, off, w, h); o += 10
        elif t == 0x00: # image
            x, y, off, w, h = struct.unpack_from('<HHIHH', buf, o + 2)
            face['background'] = decode_image(buf, off, w, h); face['bg_xy'] = (x, y); o += 14
        elif t == 0x02: # time digits HH MM
            face['time_sets'] = list(buf[o + 2:o + 6])
            face['time_xy'] = [struct.unpack_from('<HH', buf, o + 6 + 4 * i) for i in range(4)]; o += 34
        else:
            raise ValueError(f'unsupported element 0x{t:02x} at {o}')
        face['elements'].append(t)
    return face

def build_face(background, digit_sets, time_xy, time_sets=(0, 0, 1, 1), preview=None, dash=None):
    """background: 240x296 image; digit_sets: list of 10-image lists (42x66 recommended);
    time_xy: 4 (x,y) for H1 H2 M1 M2; time_sets: which digit set each position uses."""
    background = background.convert('RGBA').resize((W, H)) if background.size != (W, H) else background.convert('RGBA')
    if preview is None: preview = background.resize((140, 163))
    if dash is None: dash = Image.new('RGBA', (1, 1), (0, 0, 0, 0))
    n = len(digit_sets)
    hdr_size = 16 + 2 + 83 * n + 10 + 14 + 34 + 2
    pos = hdr_size + (-hdr_size % 4); blobs = bytearray()
    def add(img):
        nonlocal pos
        b = encode_image(img); o = pos; blobs.extend(b); pos += len(b); return o
    dash_off = add(dash); bg_off = add(background)
    digit_offs = [[add(g) for g in s] for s in digit_sets]
    prev_off = add(preview)
    out = bytearray(struct.pack('<HHIHHHH', 0x23, 0xFFFF, prev_off, *preview.size, 16, 16 + 2 + 83 * n))
    out += b'\x01\x01'
    for si, s in enumerate(digit_sets):
        out.append(si)
        for g, o in zip(s, digit_offs[si]): out += struct.pack('<IHH', o, *g.size)
        out += struct.pack('<H', 0x0101 if si == 0 else 0)
    out += b'\x01\x23' + struct.pack('<IHH', dash_off, *dash.size)
    out += b'\x01\x00' + struct.pack('<HHIHH', 0, 0, bg_off, W, H)
    out += b'\x01\x02' + bytes(time_sets) + b''.join(struct.pack('<HH', *xy) for xy in time_xy) + b'\0' * 12
    out += b'\0\0'; out += b'\0' * (-len(out) % 4)
    assert len(out) == hdr_size + (-hdr_size % 4)
    return bytes(out + blobs)
