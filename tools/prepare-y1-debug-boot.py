"""Prepare a local MT6572 boot image with USB ADB for one public host key.

Does not connect to or flash a device. The input kernel and boot addresses stay
unchanged; ADB uses host-key authentication. Root mode is an explicit option
for vendor builds with broken run-as and adb-root behavior.
"""
import argparse
import gzip
import hashlib
import struct
from pathlib import Path


def align(size, boundary):
    return (size + boundary - 1) // boundary * boundary


def unpack_cpio(data):
    records = []
    position = 0
    while position + 110 <= len(data):
        if data[position:position + 6] != b'070701':
            raise ValueError('Expected newc ramdisk')
        fields = [int(data[position + 6 + i * 8:position + 14 + i * 8], 16) for i in range(13)]
        size, namesize = fields[6], fields[11]
        name = data[position + 110:position + 110 + namesize - 1].decode('utf-8')
        start = align(position + 110 + namesize, 4)
        payload = data[start:start + size]
        if len(payload) != size:
            raise ValueError('Truncated CPIO entry')
        if name == 'TRAILER!!!':
            return records
        records.append([name, fields, payload])
        position = align(start + size, 4)
    raise ValueError('Missing CPIO trailer')


def pack_cpio(records):
    output = bytearray()
    trailer = [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 11, 0]
    for name, original_fields, payload in records + [['TRAILER!!!', trailer, b'']]:
        encoded = name.encode('utf-8') + b'\0'
        fields = original_fields.copy()
        fields[6], fields[11] = len(payload), len(encoded)
        output += b'070701' + ''.join(f'{value:08x}' for value in fields).encode('ascii')
        output += encoded
        output += b'\0' * (align(len(output), 4) - len(output))
        output += payload
        output += b'\0' * (align(len(output), 4) - len(output))
    output += b'\0' * (align(len(output), 512) - len(output))
    return bytes(output)


def prepare(source, public_key, destination, root_adbd=False, adbd=None):
    image = source.read_bytes()
    if image[:8] != b'ANDROID!':
        raise ValueError('Not an Android boot image')
    kernel_size, ramdisk_size, second_size = [struct.unpack_from('<I', image, offset)[0] for offset in (8, 16, 24)]
    page = struct.unpack_from('<I', image, 36)[0]
    if page != 2048 or second_size or struct.unpack_from('<I', image, 40)[0]:
        raise ValueError('Unsupported boot layout')
    kernel = image[page:page + kernel_size]
    ramdisk_start = page + align(kernel_size, page)
    ramdisk = image[ramdisk_start:ramdisk_start + ramdisk_size]
    if ramdisk[:4] != bytes.fromhex('88168858') or ramdisk[8:14] != b'ROOTFS' or ramdisk[512:514] != b'\x1f\x8b':
        raise ValueError('Expected MTK-wrapped gzip ROOTFS')
    key = public_key.read_text('ascii').strip()
    if not key or '\n' in key or len(key) > 4096:
        raise ValueError('Expected one public ADB key')
    records = unpack_cpio(gzip.decompress(ramdisk[512:]))
    replacement_adbd = adbd.read_bytes() if adbd else None
    if replacement_adbd is not None:
        if not root_adbd:
            raise ValueError('Replacement ATA adbd is for explicit diagnostic root mode only')
        if hashlib.sha256(replacement_adbd).hexdigest() != 'fdef97d62e53744142f4df46352faf889ee069f677c56c5a72cb39b8a9e4d984':
            raise ValueError('Expected the verified ATA Type-A diagnostic adbd')
    names = [record[0] for record in records]
    if names.count('default.prop') != 1 or names.count('init.rc') != 1 or 'adb_keys' in names:
        raise ValueError('Unexpected ramdisk entries')
    for record in records:
        if record[0] == 'default.prop':
            content = record[2].decode('ascii')
            if 'ro.secure=1' not in content or 'ro.debuggable=0' not in content:
                raise ValueError('Unexpected stock security properties')
            content = content.replace('ro.debuggable=0', 'ro.debuggable=1')
            if root_adbd:
                content = content.replace('ro.secure=1', 'ro.secure=0')
            content += '\nro.adb.secure=1\n'
            record[2] = content.encode('ascii')
        elif record[0] == 'init.rc':
            record[2] += (b'\n# OnLoopio development USB bootstrap\n'
                          b'on boot\n    setprop persist.sys.usb.config mass_storage,adb\n')
        elif record[0] == 'sbin/adbd' and replacement_adbd is not None:
            record[2] = replacement_adbd
    inode = max(record[1][0] for record in records) + 1
    records.append(['adb_keys', [inode, 0o100644, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0], (key + '\n').encode('ascii')])
    cpio = pack_cpio(records)
    if len(unpack_cpio(cpio)) != len(records):
        raise ValueError('CPIO roundtrip failed')
    packed = gzip.compress(cpio, mtime=0)
    mtk_header = bytearray(ramdisk[:512])
    struct.pack_into('<I', mtk_header, 4, len(packed))
    new_ramdisk = bytes(mtk_header) + packed
    header = bytearray(image[:page])
    struct.pack_into('<I', header, 16, len(new_ramdisk))
    boot_id = hashlib.sha1()
    for segment in (kernel, new_ramdisk, b''):
        boot_id.update(segment)
        boot_id.update(struct.pack('<I', len(segment)))
    header[576:608] = boot_id.digest() + b'\0' * 12
    output = bytes(header) + kernel + b'\0' * (align(kernel_size, page) - kernel_size)
    output += new_ramdisk + b'\0' * (align(len(new_ramdisk), page) - len(new_ramdisk))
    if len(output) > len(image):
        raise ValueError('Modified boot image exceeds source partition size')
    output += b'\0' * (len(image) - len(output))
    if output[page:page + kernel_size] != kernel:
        raise ValueError('Kernel changed')
    destination.write_bytes(output)
    print('Prepared', destination, len(output), 'bytes; kernel unchanged; authenticated USB ADB; root:', root_adbd)
    print('SHA256', hashlib.sha256(output).hexdigest())


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path)
    parser.add_argument('public_key', type=Path)
    parser.add_argument('destination', type=Path)
    parser.add_argument('--root-adbd', action='store_true')
    parser.add_argument('--adbd', type=Path, help='Verified ATA Type-A adbd for temporary diagnostic root mode')
    args = parser.parse_args()
    prepare(args.source, args.public_key, args.destination, args.root_adbd, args.adbd)
