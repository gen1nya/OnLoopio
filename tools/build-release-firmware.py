"""Build the generic Y1 Type A firmware from pinned upstream archives on Linux."""
import argparse
import hashlib
import json
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile
from pathlib import Path
from importlib import import_module

radio = import_module('prepare-y1-radio')
home = import_module('prepare-y1-home')
boot = import_module('prepare-y1-debug-boot')

STOCK_URL = 'https://github.com/y1-community/y1-stock-rom/releases/download/Latest-3.1.2/rom.zip'
ATA_URL = 'https://github.com/y1-community/y1-ata-rom/releases/download/0.1/rom.zip'
STOCK_SHA = '4a9a7719024b953e64bb35c21052628e35558cadb163416846e007ce65e40dfc'
ATA_SHA = 'd3b79ca460a3d3ec2c41ba57893127a0f9a90278e374dd33cdb5de5bb46e367e'
SYSTEM_SIZE = 0x28a00000


def fetch(source, url, expected, destination):
    if source:
        shutil.copyfile(source, destination)
    else:
        with urllib.request.urlopen(url, timeout=90) as response, destination.open('wb') as output:
            shutil.copyfileobj(response, output, 4 * 1024 * 1024)
    if radio.sha(destination) != expected:
        raise ValueError('Upstream archive checksum mismatch: ' + destination.name)


def extract(archive, name, destination):
    with zipfile.ZipFile(archive) as content:
        with content.open(name) as source, destination.open('wb') as output:
            shutil.copyfileobj(source, output, 4 * 1024 * 1024)


def fsck(path, repair):
    command = ['/usr/sbin/e2fsck', '-f', '-y' if repair else '-n', str(path)]
    result = subprocess.run(command, capture_output=True, text=True)
    if result.returncode not in ((0, 1) if repair else (0,)):
        raise RuntimeError('Filesystem validation failed: ' + result.stdout[-2000:] + result.stderr[-2000:])


def verify_boot(path):
    header, kernel, ramdisk = radio.segments(path)
    import gzip
    records = boot.unpack_cpio(gzip.decompress(ramdisk[512:]))
    if any(entry[0] == 'adb_keys' for entry in records):
        raise ValueError('Public boot contains an ADB host key')
    defaults = [entry[2] for entry in records if entry[0] == 'default.prop']
    if len(defaults) != 1 or b'ro.secure=1' not in defaults[0] or b'ro.debuggable=0' not in defaults[0]:
        raise ValueError('Public boot has unsafe debug properties')
    if b'persist.sys.usb.config mass_storage' not in b''.join(entry[2] for entry in records if entry[0] == 'init.rc'):
        raise ValueError('Public boot does not set USB storage mode')


def verify_no_seed(system):
    for path in ('/etc/onloopio-config.json', '/etc/onloopio-seed.sh', '/etc/onloopio-seed.id'):
        result = radio.run(['/usr/sbin/debugfs', '-R', 'stat ' + path, radio.linux(system)])
        if 'File not found' not in result:
            raise ValueError('Public system image contains a private seed: ' + path)


def build(args):
    if args.output.exists() and any(args.output.iterdir()):
        raise ValueError('Choose an empty output directory')
    args.output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='onloopio-firmware-') as temporary:
        work = Path(temporary)
        stock_archive, ata_archive = work / 'stock.zip', work / 'ata.zip'
        fetch(args.stock_archive, STOCK_URL, STOCK_SHA, stock_archive)
        fetch(args.ata_archive, ATA_URL, ATA_SHA, ata_archive)
        stock_boot, stock_system = work / 'stock-boot.img', work / 'stock-system.img'
        ata_boot, ata_system = work / 'ata-boot.img', work / 'ata-system.img'
        for archive, name, destination in ((stock_archive, 'boot.img', stock_boot),
                                           (stock_archive, 'system.img', stock_system),
                                           (ata_archive, 'boot.img', ata_boot),
                                           (ata_archive, 'system.img', ata_system)):
            extract(archive, name, destination)
        if stock_boot.stat().st_size != 0x600000 or stock_system.stat().st_size > SYSTEM_SIZE or ata_system.stat().st_size != SYSTEM_SIZE:
            raise ValueError('Unexpected source image sizes')
        with stock_system.open('r+b') as image:
            image.truncate(SYSTEM_SIZE)
        fsck(stock_system, True)
        fsck(stock_system, False)
        radio_dir = work / 'radio'
        radio.prepare(stock_boot, stock_system, ata_boot, ata_system, radio_dir)
        radio_manifest = radio_dir / 'manifest.json'
        home_dir = work / 'home'
        home.prepare(radio_dir / 'boot-radio-adb.img', radio_dir / 'system-radio-3.1.2.img',
                     args.apk, home_dir, radio_manifest, aapt=args.aapt, public=True)
        boot_image, system_image = home_dir / 'boot-onloopio.img', home_dir / 'system-onloopio.img'
        verify_boot(boot_image)
        verify_no_seed(system_image)
        manifest = json.loads((home_dir / 'manifest.json').read_text('utf-8'))
        manifest.update({'model': 'Innioasis Y1 verified Type A profile',
                         'stock_archive_sha256': STOCK_SHA, 'ata_archive_sha256': ATA_SHA,
                         'personalized': False, 'usb_debugging': False})
        version = manifest['version']
        if args.version != version:
            raise ValueError('Requested version differs from APK version')
        stage = work / 'package';stage.mkdir()
        shutil.copyfile(boot_image, stage / 'boot.img')
        shutil.copyfile(system_image, stage / 'system.img')
        extract(stock_archive, 'MT6572_Android_scatter.txt', stage / 'MT6572_Android_scatter.txt')
        (stage / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n', 'utf-8')
        for script in ('install-firmware.ps1', 'setup-usb.ps1'):
            shutil.copyfile(Path(__file__).parent / script, stage / script)
        package = args.output / ('OnLoopio-Y1-TypeA-v' + version + '.zip')
        with zipfile.ZipFile(package, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
            for file in sorted(stage.iterdir()):
                archive.write(file, file.name)
        shutil.copyfile(args.apk, args.output / ('OnLoopio-v' + version + '.apk'))
        digest = '\n'.join(radio.sha(file) + '  ' + file.name for file in sorted(args.output.iterdir())) + '\n'
        (args.output / 'SHA256SUMS.txt').write_text(digest, 'ascii')
        print('Generic firmware package:', package, 'bytes:', package.stat().st_size)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', required=True, type=Path)
    parser.add_argument('--aapt', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--version', required=True)
    parser.add_argument('--stock-archive', type=Path, help='Use a local copy after verifying its pinned hash')
    parser.add_argument('--ata-archive', type=Path, help='Use a local copy after verifying its pinned hash')
    build(parser.parse_args())
