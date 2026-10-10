#!/usr/bin/env python3
"""Generate small offline previews from the catalog's exact pinned fonts.
Font binaries are used in memory, not shipped. Preserve individual licenses.
Requires Pillow and fontTools. Re-run explicitly when refreshing the catalog.
"""
import concurrent.futures, hashlib, io, json, pathlib, time, urllib.parse, urllib.request
from PIL import Image, ImageDraw, ImageFont
from fontTools.ttLib import TTFont

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'app/src/main/assets/google-fonts'

def main():
    lines = (ASSETS / 'catalog.tsv').read_text().splitlines()
    revision = lines[0].split('\t')[1]
    records = [line.split('\t') for line in lines[2:] if line.strip()]
    previews = ASSETS / 'previews'; previews.mkdir(exist_ok=True)
    licenses = ASSETS / 'preview-licenses'; licenses.mkdir(exist_ok=True)
    def fetch(path, size, blob):
        url = f'https://raw.githubusercontent.com/google/fonts/{revision}/' + urllib.parse.quote(path, safe='/')
        for attempt in range(4):
            try:
                with urllib.request.urlopen(url, timeout=25) as response:
                    data = response.read(int(size) + 1)
                assert len(data) == int(size)
                assert hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest() == blob
                return data
            except Exception:
                if attempt == 3: raise
                time.sleep(1 + attempt)
    def process(r):
        name, category, path, size, blob, license_path, license_size, license_blob = r
        dest = previews / (blob + '.png')
        license_dest = licenses / (license_blob + '.txt')
        if dest.exists() and license_dest.exists(): return name, None
        try:
            data = fetch(path, size, blob)
            tt = TTFont(io.BytesIO(data)); cmap = tt.getBestCmap() or {}
            if not all(ord(ch) in cmap for ch in '0123456789'):
                return name, 'Font does not map all ten ASCII digits'
            if not license_dest.exists(): license_dest.write_bytes(fetch(license_path, license_size, license_blob))
            def render(render_data):
                # Rasterize default axes; same source file as downloaded Android fonts.
                font_size = 52
                font = ImageFont.truetype(io.BytesIO(render_data), font_size)
                text = '10:54   0123456789'
                box = font.getbbox(text)
                while box[2] - box[0] > 688 and font_size > 18:
                    font_size -= 1; font = ImageFont.truetype(io.BytesIO(render_data), font_size); box = font.getbbox(text)
                mask = Image.new('L', (720, 96))
                draw = ImageDraw.Draw(mask)
                draw.text((12 - box[0], (96 - (box[3] - box[1])) / 2 - box[1]), text, font=font, fill=255)
                assert mask.getbbox() is not None
                image = Image.new('RGBA', mask.size, (255, 255, 255, 0)); image.putalpha(mask)
                image.save(dest, optimize=True)
            try:
                render(data)
            except OSError as error:
                if 'execution context too long' not in str(error): raise
                # Keep original outlines/default axes; discard only problematic bytecode hints.
                for tag in ('fpgm', 'prep', 'cvt '):
                    if tag in tt: del tt[tag]
                if 'glyf' in tt:
                    for glyph in tt['glyf'].glyphs.values(): glyph.removeHinting()
                clean = io.BytesIO(); tt.save(clean); render(clean.getvalue())
                print('Unhinted preview:', name, flush=True)
            return name, None
        except Exception as e:
            return name, type(e).__name__ + ': ' + str(e)
    failures = {}; completed = 0
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        for name, error in pool.map(process, records):
            completed += 1
            if error: failures[name] = error; print('FAILED', name, error, flush=True)
            if completed % 100 == 0: print('Completed', completed, '/', len(records), flush=True)
    (ASSETS / 'preview-exceptions.json').write_text(json.dumps(failures, indent=2) + '\n')
    print('Finished', completed, 'exceptions', len(failures), 'PNG bytes', sum(p.stat().st_size for p in previews.glob('*.png')), flush=True)
    # Ineligible digit sets can be represented honestly; transport errors are not accepted.
    bad = {k:v for k,v in failures.items() if v != 'Font does not map all ten ASCII digits'}
    if bad: raise RuntimeError(bad)
if __name__ == '__main__': main()
