#!/usr/bin/env python3
"""Compare two APKs the way F-Droid does for reproducible builds: ignore the signature
(META-INF/*) and require every other entry to have the same CRC-32."""
import sys
import zipfile


def entries(path):
    with zipfile.ZipFile(path) as z:
        return {i.filename: i.CRC for i in z.infolist() if not i.filename.startswith("META-INF/")}


def main():
    a, b = entries(sys.argv[1]), entries(sys.argv[2])
    diff = [n for n in sorted(set(a) | set(b)) if a.get(n) != b.get(n)]
    print(f"{len(a)} entries vs {len(b)} entries (META-INF ignored)")
    if diff:
        print("NOT reproducible. Differing entries:")
        for n in diff:
            print("  ", n, "(missing in one APK)" if (n not in a or n not in b) else "")
        return 1
    print("OK: APKs are identical apart from the signature")
    return 0


if __name__ == "__main__":
    sys.exit(main())
