"""Prepare, but never flash, official Y1 3.1.2 with omitted ATA radio components.

Inputs must be the verified extracted releases. The stock system and authenticated
ADB ramdisk are retained. By default the official kernel/modules are retained;
only the WLAN module, hald/loader, supplicant/WAPI library, four firmware files and
two radio properties are added. Existing dynamic dependencies must match ATA
byte for byte. The optional ATA-kernel mode is historical diagnostics: it lacks
/proc/tpd_keys_enable required by the stock 3.1.2 player.
Requires WSL Ubuntu-24.04 debugfs/e2fsck. Device compatibility is checked separately
by prepare-y1-deployment.ps1 against the actual readback partition map.
"""
import argparse
import hashlib
import json
import os
import re
import shutil
import struct
import subprocess
from pathlib import Path
from importlib import import_module

boot_tools = import_module('prepare-y1-debug-boot')


def sha(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def linux(path):
    if os.name != 'nt':
        return str(path.resolve())
    value = str(path.resolve()).replace('\\', '/')
    if not re.match(r'^[A-Za-z]:/[^\s"]+$', value):
        raise ValueError('Use an absolute Windows workspace path without spaces')
    return '/mnt/' + value[0].lower() + value[2:]


def run(command):
    command = (['wsl', '-d', 'Ubuntu-24.04', '--'] + command) if os.name == 'nt' else command
    result = subprocess.run(command,
                            text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout + result.stderr


def segments(path):
    data = path.read_bytes()
    if data[:8] != b'ANDROID!' or struct.unpack_from('<I', data, 36)[0] != 2048:
        raise ValueError('Expected page-2048 Android boot image')
    ks, rs, second = [struct.unpack_from('<I', data, n)[0] for n in (8, 16, 24)]
    if second or struct.unpack_from('<I', data, 40)[0]:
        raise ValueError('Unsupported boot segments')
    return data[:2048], data[2048:2048+ks], data[2048+boot_tools.align(ks, 2048):][:rs]


def prepare(stock_boot, stock_system, ata_boot, ata_system, destination, stock_kernel=True):
    destination.mkdir(parents=True, exist_ok=False)
    header, original_kernel, ramdisk = segments(stock_boot)
    ata_header, kernel, _ = segments(ata_boot)
    for n in (12, 20, 28, 32, 36):
        if header[n:n+4] != ata_header[n:n+4]:
            raise ValueError('ATA boot addresses differ')
    expected_kernel = 'f56b5a205bfc935e80074ee777c27846f5dda5c80f2fa81e6e3a5ed78c92eec1'
    if hashlib.sha256(kernel).hexdigest() != expected_kernel:
        raise ValueError('Expected verified ATA Type-A kernel')
    if stock_kernel:
        kernel = original_kernel
        expected_kernel = '2716c37e9002da7f0c4465bf3a7a6e79a86eb3d0aaa2faaf9e6b9d550c4a05ec'
        if hashlib.sha256(kernel).hexdigest() != expected_kernel:
            raise ValueError('Expected verified official stock 3.1.2 kernel')
    header = bytearray(header)
    struct.pack_into('<I', header, 8, len(kernel))
    identifier = hashlib.sha1()
    for part in (kernel, ramdisk, b''):
        identifier.update(part)
        identifier.update(struct.pack('<I', len(part)))
    header[576:608] = identifier.digest() + b'\0' * 12
    image = bytes(header) + kernel + b'\0' * (boot_tools.align(len(kernel), 2048)-len(kernel))
    image += ramdisk + b'\0' * (boot_tools.align(len(ramdisk), 2048)-len(ramdisk))
    if len(image) > 0x600000:
        raise ValueError('Boot partition overflow')
    new_boot = destination / 'boot-radio-adb.img'
    new_boot.write_bytes(image + b'\0' * (0x600000-len(image)))
    if segments(new_boot)[2] != ramdisk:
        raise ValueError('Input authenticated ADB ramdisk changed')

    extracted = destination / 'components'
    extracted.mkdir()
    modules = ['ccci', 'ccci_plat', 'devapc', 'devinfo', 'hid-logitech-dj',
               'mtk_fm_drv', 'mtk_stp_bt', 'mtk_stp_gps', 'mtk_stp_wmt',
               'mtk_wmt_wifi', 'scsi_tgt', 'scsi_wait_scan', 'sec',
               'vcodec_kernel_driver', 'wlan_mt6582']
    if stock_kernel:
        # Retain the official 3.1.2 module set. Only the omitted WLAN module
        # is added; runtime ABI and radio tests are required before deployment
        # can be described as validated on this newer kernel.
        modules = ['wlan_mt6582']
    files = [(f'/lib/modules/{name}.ko', 0o100644) for name in modules]
    files += [('/bin/wlan_loader', 0o100755), ('/bin/hald', 0o100755), ('/bin/wpa_supplicant', 0o100755)]
    files += [('/lib/libwapi.so', 0o100644)]
    files += [(f'/etc/firmware/{name}', 0o100644) for name in
              ['WIFI_RAM_CODE', 'WIFI_RAM_CODE_E6', 'WIFI_RAM_CODE_MT6582', 'WIFI_RAM_CODE_MT6628']]
    commands = []
    for name, mode in files:
        local = extracted / name.strip('/').replace('/', '__')
        commands.append(f'dump {name} {linux(local)}')
    script = destination / 'extract.debugfs'
    script.write_text('\n'.join(commands)+'\n')
    run(['/usr/sbin/debugfs', '-f', linux(script), linux(ata_system)])
    for name, mode in files:
        local = extracted / name.strip('/').replace('/', '__')
        if not local.exists() or not local.stat().st_size:
            raise ValueError('Missing ATA component: ' + name)
        if name.endswith('.ko') and b'vermagic=3.4.5 SMP preempt mod_unload ARMv7 ' not in local.read_bytes():
            raise ValueError('Unexpected module ABI: ' + name)

    # Follow the full ELF dependency closure before preparing a deployable image.
    # Radio executables need vendor libraries which stock may omit (e.g. libwapi).
    pending = ['/bin/wlan_loader', '/bin/hald', '/bin/wpa_supplicant']
    dependencies = {}
    replacements = {name for name, mode in files}
    while pending:
        name = pending.pop()
        if name in dependencies:
            continue
        local = extracted / name.strip('/').replace('/', '__')
        if name not in replacements:
            run(['/usr/sbin/debugfs', '-R', f'dump {name} {linux(local)}', linux(ata_system)])
            stock_local = extracted / ('stock__' + name.strip('/').replace('/', '__'))
            run(['/usr/sbin/debugfs', '-R', f'dump {name} {linux(stock_local)}', linux(stock_system)])
            if not stock_local.exists() or not local.exists() or stock_local.read_bytes() != local.read_bytes():
                raise ValueError('Missing or differing dynamic dependency: ' + name)
        dynamic = run(['/usr/bin/readelf', '-d', linux(local)])
        needed = re.findall(r'\(NEEDED\).*?Shared library: \[([^\]]+)\]', dynamic)
        dependencies[name] = needed
        pending += ['/lib/' + library for library in needed]
    (destination / 'dependencies.json').write_text(json.dumps(dependencies, indent=2))

    system = destination / 'system-radio-3.1.2.img'
    shutil.copyfile(stock_system, system)
    prop = extracted / 'build.prop'
    run(['/usr/sbin/debugfs', '-R', f'dump /build.prop {linux(prop)}', linux(system)])
    text = prop.read_text(encoding='utf-8')
    if 'ro.build.fingerprint=Timmkoo@1780738034' not in text:
        raise ValueError('Expected stock 3.1.2 system')
    text += '\n# ATA Type-A radio support; stock 3.1.2 framework retained\nmediatek.wlan.chip=MT6572\nmediatek.wlan.module.postfix=_mt6572\n'
    prop.write_text(text, encoding='utf-8', newline='\n')
    commands = []
    for name, mode in files + [('/build.prop', 0o100644)]:
        local = prop if name == '/build.prop' else extracted / name.strip('/').replace('/', '__')
        # rm reports missing paths for added files; each write is verified below.
        commands += [f'rm {name}', f'write {linux(local)} {name}',
                     f'set_inode_field {name} mode {mode:07o}',
                     f'set_inode_field {name} uid 0', f'set_inode_field {name} gid 0']
    commands += ['symlink /lib/modules/wlan.ko wlan_mt6582.ko']
    script = destination / 'patch.debugfs'
    script.write_text('\n'.join(commands)+'\n')
    (destination / 'debugfs.log').write_text(run(['/usr/sbin/debugfs', '-w', '-f', linux(script), linux(system)]))
    (destination / 'e2fsck.log').write_text(run(['/usr/sbin/e2fsck', '-f', '-n', linux(system)]))
    check = destination / 'check.bin'
    for name, mode in files + [('/build.prop', 0o100644)]:
        local = prop if name == '/build.prop' else extracted / name.strip('/').replace('/', '__')
        run(['/usr/sbin/debugfs', '-R', f'dump {name} {linux(check)}', linux(system)])
        if check.read_bytes() != local.read_bytes():
            raise ValueError('System patch verification failed: ' + name)
    if system.stat().st_size != 0x28a00000:
        raise ValueError('Unexpected system partition length')
    manifest = {'status': 'prepared_not_flashed', 'stock_system': sha(stock_system),
                'ata_boot': sha(ata_boot), 'ata_system': sha(ata_system),
                'kernel': expected_kernel, 'boot': sha(new_boot), 'system': sha(system),
                'kernel_source': 'official-stock-3.1.2' if stock_kernel else 'ATA-Type-A',
                'transplanted_files': [name for name, mode in files],
                'preserved': ['stock 3.1.2 framework/APK', 'input stock ramdisk',
                              'data/calibration/partition tables']}
    (destination / 'manifest.json').write_text(json.dumps(manifest, indent=2))
    print('Prepared radio images; verified bytes, boot layout and e2fsck; no device accessed.')
    print(new_boot, system)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for arg in ['stock_boot', 'stock_system', 'ata_boot', 'ata_system', 'destination']:
        parser.add_argument(arg, type=Path)
    parser.add_argument('--ata-kernel', dest='stock_kernel', action='store_false', default=True,
                        help='Historical diagnostic mode; ATA kernel is incompatible with the stock 3.1.2 key-lock interface')
    args = parser.parse_args()
    prepare(**vars(args))
