#!/usr/bin/env python3
"""Build the offline family index. Does not embed or download any font binaries.
Google Fonts metadata + complete, commit-pinned google/fonts tree; standard library only.
"""
import json, urllib.request, collections, pathlib, datetime
ROOT = pathlib.Path(__file__).resolve().parents[1]
def fetch(url):
    with urllib.request.urlopen(url, timeout=60) as response:
        return json.load(response)
def generate(metadata, repo, tree):
    assert not tree['truncated'], 'Refusing to generate an incomplete catalog'
    folders = collections.defaultdict(list)
    for item in tree['tree']:
        parts = item['path'].split('/')
        if len(parts) >= 3 and parts[0] in ('ofl', 'apache', 'ufl'):
            folders['/'.join(parts[:2])].append(item)
    lookup = {path.split('/')[1]: path for path in folders}
    rows = []
    for family in metadata['familyMetadataList']:
        if 'latin' not in family['subsets'] or family.get('colorCapabilities'): continue
        name = ''.join(c for c in family['family'].lower() if c.isalnum())
        folder = lookup.get(name)
        if not folder: continue
        files = folders[folder]
        fonts = [f for f in files if f['path'].endswith('.ttf') and 'italic' not in f['path'].lower() and 12 <= f.get('size', 0) <= 4 * 1024 * 1024]
        license_file = next((f for f in files if f['path'].rsplit('/', 1)[-1] in ('OFL.txt', 'LICENSE.txt', 'LICENCE.txt', 'UFL.txt')), None)
        if not fonts or not license_file or not 0 < license_file['size'] <= 65536: continue
        def score(f):
            name = f['path'].rsplit('/', 1)[-1].lower()
            return (0 if '-regular.ttf' in name else 1 if '[' in name else 2 if '-medium.ttf' in name else 3, len(name), name)
        font = min(fonts, key=score)
        rows.append((family.get('popularity', 99999), [family['family'], family['category'], font['path'], str(font['size']), font['sha'], license_file['path'], str(license_file['size']), license_file['sha']]))
    lines = [f"# google/fonts\t{repo['sha']}", '# family\tcategory\tfontPath\tbytes\tgitBlob\tlicensePath\tlicenseBytes\tlicenseGitBlob']
    for _, row in sorted(rows, key=lambda r: (r[0], r[1][0])):
        assert all('\t' not in v and '\n' not in v for v in row)
        lines.append('\t'.join(row))
    out = ROOT / 'app/src/main/assets/google-fonts/catalog.tsv'
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text('\n'.join(lines) + '\n')
    print(f'{len(rows)} font families; pinned source {repo["sha"]}')
if __name__ == '__main__':
    repo = fetch('https://api.github.com/repos/google/fonts/commits/main')
    generate(fetch('https://fonts.google.com/metadata/fonts'), repo,
             fetch('https://api.github.com/repos/google/fonts/git/trees/' + repo['sha'] + '?recursive=1'))
