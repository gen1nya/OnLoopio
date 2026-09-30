"""Fail closed on obvious private paths, artifacts and values in the Git index."""
import pathlib
import hashlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
names = subprocess.check_output(['git', 'ls-files', '-z'], cwd=ROOT).decode().split('\0')
errors = []
deny_extensions = {'.apk', '.aab', '.img', '.bin', '.zip', '.7z', '.tar', '.gz',
                   '.jks', '.keystore', '.pem', '.key', '.p12', '.pfx', '.db',
                   '.sqlite', '.log', '.env', '.crt', '.cer', '.der'}
allow_binary = {'gradle/wrapper/gradle-wrapper.jar'}
reviewed_fixtures = {
    'fixture.aac': 'eea0fed5ff8b5552c51513d7071ff7a95619a5a6629a50ac13d512cbeba2b54f',
    'fixture.flac': '9f0becfc5161fe14e4d58bd72aa2a042e4bf273f08f3ec5f9eb3bebbdb57c1ae',
    'fixture.m4a': '0bf06c67805ed62f187decc29b26d93e50191effd5a43d20a4507569a9c31fe6',
    'fixture.mp3': '83f64f32cc8a4d4246de951a3d96c7a305c3f8f398db490fd14a1f5602166271',
    'fixture.ogg': '6b39e610e612dd3e0faf0cb3562f9d31f03b0d1debc1ae24abd777e756d8d46a',
    'fixture.wav': 'd79430ec951e881a46f0a93481274dceac2407219426a2ff89aebe51df3ab8d2',
}
patterns = [
    re.compile(rb'(?i)navidrome\.home\.arpa'),
    re.compile(rb'(?i)C:\\Users\\[^\\\s]+'),
    re.compile(rb'(?i)-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'),
    re.compile(rb'(?i)(?:AKIA|ASIA)[0-9A-Z]{16}'),
    re.compile(rb'(?i)gh[pousr]_[A-Za-z0-9_]{30,}'),
    re.compile(rb'(?i)(?:password|token|api_key|secret)\s*[:=]\s*["\'][^"\']{8,}["\']'),
]
for name in filter(None, names):
    path = pathlib.PurePosixPath(name)
    if (path.parts[0] in {'local', '.local', '.tools', '.research', 'secrets'}
            or path.suffix.lower() in deny_extensions
            or 'backup' in path.parts or 'dump' in path.parts):
        errors.append('private path: ' + name)
        continue
    content = (ROOT / name).read_bytes()
    if name.startswith('app/src/androidTest/assets/music/'):
        if reviewed_fixtures.get(path.name) != hashlib.sha256(content).hexdigest():
            errors.append('unreviewed audio fixture: ' + name)
        continue
    if b'\0' in content and name not in allow_binary and path.suffix.lower() != '.png':
        errors.append('unreviewed binary: ' + name)
        continue
    if path.suffix.lower() == '.png':
        if not name.startswith('docs/screenshots/') or len(content) > 500_000:
            errors.append('unreviewed image: ' + name)
        continue
    if name in allow_binary:
        continue
    for expression in patterns:
        if expression.search(content):
            errors.append('sensitive pattern in ' + name + ': ' + expression.pattern.decode())
if errors:
    print('\n'.join(errors), file=sys.stderr)
    sys.exit(1)
print('Public Git index checked:', len(list(filter(None, names))), 'files')
