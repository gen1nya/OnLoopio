"""Prepare OnLoopio HOME from the runtime-verified Y1 3.1.2 radio base; never flash.

Keeps the official kernel, authenticated shell ADB, framework and radio payloads.
Removes the stock player APK, installs OnLoopio and its system native library,
maps the wheel vertically for Android settings, and optionally adds a root-only
configuration seed. Requires WSL Ubuntu-24.04 debugfs/e2fsck.
"""
import argparse
import gzip
import hashlib
import json
import re
import shutil
import struct
import subprocess
import uuid
import zipfile
from importlib import import_module
from pathlib import Path
from urllib.parse import urlsplit

radio = import_module('prepare-y1-radio')
boot = import_module('prepare-y1-debug-boot')


def config_bytes(path):
    content = path.read_bytes()
    if len(content) > 32768:
        raise ValueError('Private config exceeds the app limit')
    # Do not include parser exceptions or secret-bearing values in diagnostics.
    try:
        config = json.loads(content.decode('utf-8'))
        url = urlsplit(config['url'])
        valid = (url.scheme in ('http', 'https') and url.hostname and not url.username
                 and not url.password and not url.query and not url.fragment
                 and isinstance(config['username'], str) and config['username'].strip()
                 and isinstance(config['password'], str) and config['password']
                 and isinstance(config.get('trustedCa', ''), str)
                 and len(config.get('trustedCa', '')) <= 16384)
    except Exception:
        valid = False
    if not valid:
        raise ValueError('Invalid private server configuration')
    return content


def patch_boot(source, destination, with_seed, public=False):
    header, kernel, ramdisk = radio.segments(source)
    records = boot.unpack_cpio(gzip.decompress(ramdisk[512:]))
    original = {r[0]: (r[1].copy(), r[2]) for r in records}
    default = original['default.prop'][1]
    if b'ro.secure=1' not in default:
        raise ValueError('Secure stock boot required')
    if public and (b'ro.debuggable=0' not in default or any(r[0] == 'adb_keys' for r in records)):
        raise ValueError('Public boot must have debugging disabled and no authorized host key')
    addition = (b'\n# OnLoopio HOME: stock key-lock policy (wheel locked while screen is off)\n'
                b'on boot\n    write /proc/tpd_keys_enable 0\n')
    if public:
        addition += b'\n# Public image exposes USB storage without USB debugging\non boot\n    setprop persist.sys.usb.config mass_storage\n'
    if with_seed:
        addition += (b'\nservice onloopio-seed /system/bin/sh /system/etc/onloopio-seed.sh\n'
                     b'    class main\n    user root\n    group root\n    oneshot\n')
    for record in records:
        if record[0] == 'init.rc': record[2] += addition
    for name, fields, payload in records:
        if name != 'init.rc' and (fields, payload) != original[name]:
            raise ValueError('Unexpected ramdisk change')
    packed = gzip.compress(boot.pack_cpio(records), mtime=0)
    mtk = bytearray(ramdisk[:512]); struct.pack_into('<I', mtk, 4, len(packed))
    new_ramdisk = bytes(mtk) + packed
    new_header = bytearray(header); struct.pack_into('<I', new_header, 16, len(new_ramdisk))
    identifier = hashlib.sha1()
    for segment in (kernel, new_ramdisk, b''):
        identifier.update(segment); identifier.update(struct.pack('<I', len(segment)))
    new_header[576:608] = identifier.digest() + b'\0' * 12
    output = bytes(new_header) + kernel + b'\0' * (boot.align(len(kernel), 2048) - len(kernel))
    output += new_ramdisk + b'\0' * (boot.align(len(new_ramdisk), 2048) - len(new_ramdisk))
    if len(output) > 0x600000: raise ValueError('Boot image exceeds partition')
    destination.write_bytes(output + b'\0' * (0x600000-len(output)))
    if radio.segments(destination)[1] != kernel: raise ValueError('Kernel changed')


def prepare(source_boot, source_system, apk, destination, radio_manifest, config=None,
            aapt=Path('C:/Android/build-tools/34.0.0/aapt.exe'), public=False):
    evidence = json.loads(radio_manifest.read_text('utf-8'))
    if (evidence.get('kernel_source') != 'official-stock-3.1.2' or
            radio.sha(source_boot) != evidence.get('boot') or radio.sha(source_system) != evidence.get('system')):
        raise ValueError('Use the verified official-kernel 3.1.2 radio build')
    if public and config:
        raise ValueError('Public firmware cannot contain a private configuration seed')
    if destination.exists(): raise ValueError('Choose a new staging directory')
    # Validate configuration before writing any staged image; never print secrets.
    seed = config_bytes(config) if config else None
    tree = subprocess.run([str(aapt), 'dump', 'xmltree', str(apk), 'AndroidManifest.xml'],
                          text=True, capture_output=True, check=True).stdout
    for required in ('package="io.onloopio"', 'io.onloopio.ui.PlaylistActivity',
                     'android.intent.category.HOME', 'android.intent.category.DEFAULT'):
        if required not in tree: raise ValueError('APK is not the OnLoopio HOME application')
    if not re.search(r'minSdkVersion[^\n]*0x11\b', tree): raise ValueError('APK must target the Y1 API-17 minimum')
    badging=subprocess.run([str(aapt),'dump','badging',str(apk)],text=True,capture_output=True,check=True).stdout
    version=re.search(r"versionName='([0-9]+\.[0-9]+\.[0-9]+)'",badging)
    if not version: raise ValueError('APK needs a semantic version')
    version=version.group(1)
    if tuple(map(int, version.split('.'))) >= (0, 8, 0) and 'android.permission.MOUNT_UNMOUNT_FILESYSTEMS' not in tree:
        raise ValueError('OnLoopio 0.8 USB storage requires the mount permission in the system APK')
    repository = Path(__file__).resolve().parent.parent
    if seed and destination.resolve().is_relative_to(repository):
        raise ValueError('Private firmware must be staged outside this repository')
    with zipfile.ZipFile(apk) as archive:
        native_libraries = {Path(name).name: archive.read(name) for name in archive.namelist()
                            if name.startswith('lib/armeabi-v7a/') and name.endswith('.so')}
    if 'libconscrypt_jni.so' not in native_libraries:
        raise ValueError('Missing ARMv7 TLS library')
    for name, native in native_libraries.items():
        if native[:4] != b'\x7fELF' or struct.unpack_from('<H',native,18)[0] != 40:
            raise ValueError('Invalid ARMv7 native library: ' + name)
        if name in ('libcrypto.so','libssl.so'):
            raise ValueError('Refusing to replace Android system SSL library')
    destination.mkdir(parents=True)
    components = destination / 'components'; components.mkdir()
    system = destination / 'system-onloopio.img'
    shutil.copyfile(source_system, system)
    payloads = {}
    def add(name, target, content, mode=0o644):
        local = components / name; local.write_bytes(content)
        payloads[target] = (local, mode)
    add('OnLoopio.apk', '/app/OnLoopio.apk', apk.read_bytes())
    # API 17 system apps load native libraries from /system/lib, not their ZIP.
    for name, native in native_libraries.items():
        add(name, '/lib/' + name, native)
    for target, name in (('/usr/keylayout/Generic.kl', 'Generic.kl'), ('/build.prop', 'build.prop')):
        local = components / name
        radio.run(['/usr/sbin/debugfs', '-R', f'dump {target} {radio.linux(local)}', radio.linux(system)])
        content = local.read_text('utf-8')
        if name == 'Generic.kl':
            for code, old, new in ((105, 'DPAD_LEFT', 'DPAD_UP'), (106, 'DPAD_RIGHT', 'DPAD_DOWN')):
                content, count = re.subn(rf'(?m)^(key\s+{code}\s+){old}\b', rf'\g<1>{new}', content)
                if count != 1: raise ValueError('Unexpected wheel keylayout')
        else:
            content, count = re.subn(r'(?m)^ro.build.display.id=.*$', 'ro.build.display.id=OnLoopio-'+version+'-Y1-3.1.2', content)
            if count != 1: raise ValueError('Unexpected base build properties')
        local.write_text(content, 'utf-8', newline='\n'); payloads[target] = (local, 0o644)
    revision = str(uuid.uuid4()) if seed else None
    if seed:
        add('onloopio-config.json', '/etc/onloopio-config.json', seed, 0o600)
        add('onloopio-seed.id', '/etc/onloopio-seed.id', (revision+'\n').encode(), 0o600)
        script = (Path(__file__).parent / 'firmware/onloopio-seed.sh').read_text('utf-8').replace('\r\n','\n').encode()
        add('onloopio-seed.sh', '/etc/onloopio-seed.sh', script, 0o700)
    commands = ['rm /app/com.innioasis.y1_3.1.2.apk']
    replace = {'/usr/keylayout/Generic.kl', '/build.prop'}
    for target, (local, mode) in payloads.items():
        if target in replace: commands.append('rm '+target)
        commands += [f'write {radio.linux(local)} {target}',
                     f'set_inode_field {target} mode 0{0o100000|mode:o}',
                     f'set_inode_field {target} uid 0', f'set_inode_field {target} gid 0']
    command_file = destination / 'patch.debugfs'; command_file.write_text('\n'.join(commands)+'\n','ascii')
    result = radio.run(['/usr/sbin/debugfs', '-w', '-f', radio.linux(command_file), radio.linux(system)])
    (destination / 'debugfs.log').write_text(result, 'utf-8')
    if "Couldn't parse" in result or 'File exists' in result or 'File not found' in result:
        raise ValueError('Filesystem patch failed; image must not be flashed')
    removed = radio.run(['/usr/sbin/debugfs', '-R', 'stat /app/com.innioasis.y1_3.1.2.apk', radio.linux(system)])
    if 'File not found' not in removed: raise ValueError('Stock player was not removed')
    for target, (local, mode) in payloads.items():
        check = destination / 'verify.bin'
        radio.run(['/usr/sbin/debugfs', '-R', f'dump {target} {radio.linux(check)}', radio.linux(system)])
        if radio.sha(check) != radio.sha(local): raise ValueError('Staged payload verification failed: '+target)
        stat = radio.run(['/usr/sbin/debugfs', '-R', 'stat '+target, radio.linux(system)])
        if not re.search(rf'Mode:\s+0*{mode:o}\b', stat) or not re.search(r'User:\s+0\s+Group:\s+0', stat):
            raise ValueError('Staged permission verification failed: '+target)
    # Last verification may contain private configuration; remove that exact scratch file.
    (destination / 'verify.bin').unlink()
    check = radio.run(['/usr/sbin/e2fsck', '-f', '-n', radio.linux(system)])
    (destination / 'e2fsck.log').write_text(check, 'utf-8')
    new_boot = destination / 'boot-onloopio.img'; patch_boot(source_boot, new_boot, seed is not None, public)
    manifest = {'status':'prepared_not_flashed', 'version':version,'base':'Y1 official-kernel 3.1.2 + verified radio payloads',
                'boot_sha256':radio.sha(new_boot), 'system_sha256':radio.sha(system), 'apk_sha256':radio.sha(apk),
                'stock_player_removed':True, 'wheel':'105 UP / 106 DOWN', 'private_seed_embedded':seed is not None,
                'seed_revision':revision, 'adb':'disabled by default' if public else 'development image'}
    (destination / 'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n','utf-8')
    print('Prepared OnLoopio HOME firmware:', destination)
    print('Boot SHA256:', manifest['boot_sha256']); print('System SHA256:', manifest['system_sha256'])
    if seed: print('This staging directory and its images contain private server configuration.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('source_boot','source_system','apk','destination'): parser.add_argument(name,type=Path)
    parser.add_argument('--radio-manifest',type=Path,required=True)
    parser.add_argument('--public',action='store_true',help='Reject private seeds and ADB host keys')
    parser.add_argument('--config',type=Path,help='Optional private seed; keep resulting images private')
    parser.add_argument('--aapt',type=Path,default=Path('C:/Android/build-tools/34.0.0/aapt.exe'))
    args = parser.parse_args()
    prepare(args.source_boot,args.source_system,args.apk,args.destination,args.radio_manifest,args.config,args.aapt,args.public)
