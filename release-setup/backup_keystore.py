#!/usr/bin/env python3
"""Authenticated, password-encrypted keystore backup; never overwrites files."""
import argparse
import getpass
import os
from pathlib import Path
from cryptography.hazmat.primitives.kdf.scrypt import Scrypt
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.exceptions import InvalidTag

MAGIC = b'LOTURA01'

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('mode', choices=['encrypt', 'decrypt'])
    p.add_argument('source', type=Path)
    p.add_argument('destination', type=Path)
    a = p.parse_args()
    if a.destination.exists():
        p.error('Refusing to overwrite destination')
    password = os.environ.get('BACKUP_PASSWORD') or getpass.getpass('Backup password: ')
    if len(password) < 16:
        p.error('Password must have at least 16 characters')
    data = a.source.read_bytes()
    if a.mode == 'encrypt':
        salt, nonce = os.urandom(16), os.urandom(12)
        header = MAGIC + salt + nonce
    else:
        if len(data) < 52 or data[:8] != MAGIC:
            p.error('Invalid backup format')
        header, salt, nonce = data[:36], data[8:24], data[24:36]
    key = Scrypt(salt=salt, length=32, n=2**17, r=8, p=1).derive(password.encode())
    try:
        result = (header + AESGCM(key).encrypt(nonce, data, header)
                  if a.mode == 'encrypt' else AESGCM(key).decrypt(nonce, data[36:], header))
    except InvalidTag:
        p.error('Wrong password or corrupted backup; no output written')
    fd = os.open(a.destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'wb') as f:
        f.write(result)
    print(f'{a.mode}: {a.destination}')

if __name__ == '__main__':
    main()