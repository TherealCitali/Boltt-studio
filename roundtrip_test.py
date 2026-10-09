from moyface import *
src = open('../inspection/face_upload.bin', 'rb').read()
f = parse_face(src)
print('elements', [hex(e) for e in f['elements']], 'time_xy', f['time_xy'], 'sets', f['time_sets'], 'bg', f['background'].size)
rebuilt = build_face(f['background'], f['digit_sets'], f['time_xy'], f['time_sets'], f['preview'], f.get('dash'))
g = parse_face(rebuilt)
same = lambda a, b: list(a.getdata()) == list(b.getdata())
ok = same(f['background'], g['background']) and same(f['preview'], g['preview']) and all(
    same(a, b) for s, t in zip(f['digit_sets'], g['digit_sets']) for a, b in zip(s, t)) and f['time_xy'] == g['time_xy']
print('original', len(src), 'rebuilt', len(rebuilt), 'pixel-identical round trip:', ok)
print('header original', src[:24].hex()); print('header rebuilt ', rebuilt[:24].hex())
