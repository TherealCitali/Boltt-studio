"""Portable reference regression test; run from any working directory."""
from pathlib import Path
from moyface import parse_face, build_face

ROOT = Path(__file__).resolve().parent

def check(path):
    source = path.read_bytes()
    face = parse_face(source)
    rebuilt = build_face(face['background'], face['digit_sets'], face['time_xy'], face['time_sets'], face['preview'], face.get('dash'))
    result = parse_face(rebuilt)
    def same(a, b):
        return a.size == b.size and list(a.getdata()) == list(b.getdata())
    assert same(face['background'], result['background'])
    assert same(face['preview'], result['preview'])
    assert same(face['dash'], result['dash'])
    assert face['time_xy'] == result['time_xy'] and face['time_sets'] == result['time_sets']
    assert all(same(a, b) for s, t in zip(face['digit_sets'], result['digit_sets']) for a, b in zip(s, t))
    print(f'{path.name}: pixel-identical round trip ({len(source)} -> {len(rebuilt)} bytes)')

if __name__ == '__main__':
    for filename in ['dafit_captured_face.bin', 'demo_face.bin']:
        check(ROOT / filename)
