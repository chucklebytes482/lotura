#!/usr/bin/env python3
"""Create a standard AES-256 7z backup with encrypted filenames."""
import argparse
import getpass
import os
from pathlib import Path
import py7zr

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('keystore', type=Path)
    p.add_argument('archive', type=Path)
    a = p.parse_args()
    if not a.keystore.is_file():
        p.error('Keystore missing')
    if a.archive.exists():
        p.error('Refusing to overwrite archive')
    password = os.environ.get('BACKUP_PASSWORD') or getpass.getpass('Backup password: ')
    if len(password) < 16:
        p.error('Use at least 16 characters')
    os.umask(0o077)
    filters = [{'id': py7zr.FILTER_LZMA2}, {'id': py7zr.FILTER_CRYPTO_AES256_SHA256}]
    with py7zr.SevenZipFile(a.archive, 'w', password=password,
                           header_encryption=True, filters=filters) as z:
        z.write(a.keystore, arcname='lotura-release-upload.jks')
    print(f'Encrypted phone backup: {a.archive}')

if __name__ == '__main__':
    main()