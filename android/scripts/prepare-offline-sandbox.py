#!/usr/bin/env python3
"""
Cross-platform offline Android sandbox asset preparer for Neon v0.3.0.
Downloads Alpine Linux aarch64 minirootfs and Termux aarch64 proot + libtalloc
from official/mirror endpoints without external CLI dependencies (ar/tar/zstd).
"""

import gzip
import io
import lzma
import os
import pathlib
import sys
import tarfile
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS_DIR = ROOT / "app" / "src" / "main" / "assets"
JNILIBS_DIR = ROOT / "app" / "src" / "main" / "jniLibs" / "arm64-v8a"

ALPINE_URLS = [
    "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.3-aarch64.tar.gz",
    "https://mirrors.tuna.tsinghua.edu.cn/alpine/v3.21/releases/aarch64/alpine-minirootfs-3.21.3-aarch64.tar.gz",
]

TERMUX_BASES = [
    "https://packages.termux.dev/apt/termux-main",
    "https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main",
]


def download_bytes(urls: list[str], desc: str) -> bytes:
    last_err = None
    for url in urls:
        try:
            print(f"Downloading {desc}: {url}")
            req = urllib.request.Request(url, headers={"User-Agent": "Neon-Builder/0.3"})
            with urllib.request.urlopen(req, timeout=60) as resp:
                data = resp.read()
            print(f"  -> OK ({len(data)} bytes)")
            return data
        except Exception as exc:
            last_err = exc
            print(f"  -> Failed: {exc}")
    raise RuntimeError(f"Failed to download {desc}: {last_err}")


def find_deb_filename(pkg_name: str) -> tuple[str, str]:
    for base in TERMUX_BASES:
        idx_url = f"{base}/dists/stable/main/binary-aarch64/Packages.gz"
        try:
            raw_gz = download_bytes([idx_url], f"Termux Packages.gz ({base})")
            text = gzip.decompress(raw_gz).decode("utf-8", errors="replace")
            cur_pkg = None
            for line in text.splitlines():
                if line.startswith("Package: "):
                    cur_pkg = line.split(": ", 1)[1].strip()
                elif line.startswith("Filename: ") and cur_pkg == pkg_name:
                    return base, line.split(": ", 1)[1].strip()
        except Exception as exc:
            print(f"  Index lookup failed on {base}: {exc}")
    raise RuntimeError(f"Package '{pkg_name}' not found in Termux index")


def iter_ar_members(deb_bytes: bytes):
    if not deb_bytes.startswith(b"!<arch>\n"):
        raise ValueError("Not a valid ar/.deb archive")
    pos = 8
    while pos + 60 <= len(deb_bytes):
        header = deb_bytes[pos : pos + 60]
        pos += 60
        name = header[:16].decode("ascii", errors="replace").strip().rstrip("/")
        size = int(header[48:58].decode("ascii").strip())
        data = deb_bytes[pos : pos + size]
        pos += size
        if pos % 2 == 1:
            pos += 1
        yield name, data


def extract_from_deb(deb_bytes: bytes, targets: dict[str, pathlib.Path]):
    for member_name, member_data in iter_ar_members(deb_bytes):
        if not member_name.startswith("data.tar"):
            continue
        if member_name.endswith(".xz"):
            tar_bytes = lzma.decompress(member_data)
        elif member_name.endswith(".gz"):
            tar_bytes = gzip.decompress(member_data)
        else:
            raise RuntimeError(f"Unsupported tar compression in .deb: {member_name}")

        with tarfile.open(fileobj=io.BytesIO(tar_bytes), mode="r:") as tf:
            for ti in tf.getmembers():
                if not ti.isfile():
                    continue
                if "/man/" in ti.name or "/doc/" in ti.name or ti.name.endswith(".gz"):
                    continue
                base = os.path.basename(ti.name)
                for pattern, dest in targets.items():
                    if base == pattern or (pattern.endswith(".so") and base.startswith(pattern)):
                        f = tf.extractfile(ti)
                        if f is not None:
                            dest.parent.mkdir(parents=True, exist_ok=True)
                            dest.write_bytes(f.read())
                            print(f"  Extracted {ti.name} -> {dest} ({dest.stat().st_size} bytes)")


def main():
    ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    JNILIBS_DIR.mkdir(parents=True, exist_ok=True)

    rootfs_out = ASSETS_DIR / "rootfs-arm64.tar.gz"
    if not rootfs_out.exists() or rootfs_out.stat().st_size < 100_000:
        rootfs_bytes = download_bytes(ALPINE_URLS, "Alpine 3.21.3 aarch64 minirootfs")
        rootfs_out.write_bytes(rootfs_bytes)
    else:
        print(f"Alpine rootfs already exists: {rootfs_out} ({rootfs_out.stat().st_size} bytes)")

    proot_so = JNILIBS_DIR / "libproot.so"
    if not proot_so.exists() or proot_so.stat().st_size < 10_000:
        base, rel = find_deb_filename("proot")
        deb = download_bytes([f"{base}/{rel}"], "Termux proot aarch64")
        extract_from_deb(
            deb,
            {
                "proot": proot_so,
                "loader": JNILIBS_DIR / "libproot-loader.so",
            },
        )
    else:
        print(f"libproot.so already exists: {proot_so} ({proot_so.stat().st_size} bytes)")

    talloc_so = JNILIBS_DIR / "libtalloc.so"
    if not talloc_so.exists() or talloc_so.stat().st_size < 10_000:
        base, rel = find_deb_filename("libtalloc")
        deb = download_bytes([f"{base}/{rel}"], "Termux libtalloc aarch64")
        extract_from_deb(deb, {"libtalloc.so": talloc_so})
    else:
        print(f"libtalloc.so already exists: {talloc_so} ({talloc_so.stat().st_size} bytes)")

    shmem_so = JNILIBS_DIR / "libandroid-shmem.so"
    if not shmem_so.exists() or shmem_so.stat().st_size < 4_000:
        base, rel = find_deb_filename("libandroid-shmem")
        deb = download_bytes([f"{base}/{rel}"], "Termux libandroid-shmem aarch64")
        extract_from_deb(deb, {"libandroid-shmem.so": shmem_so})
    else:
        print(f"libandroid-shmem.so already exists: {shmem_so} ({shmem_so.stat().st_size} bytes)")

    # Termux 的 proot 依赖 libtalloc.so.2，但 APK 里只能放 lib*.so 命名的文件，
    # 所以把 DT_NEEDED 和 SONAME 原地改成 libtalloc.so。
    for so in (proot_so, talloc_so):
        rename_dynstr(so, b"libtalloc.so.2\0", b"libtalloc.so\0\0\0")
    for so in (proot_so, talloc_so, shmem_so):
        print(f"{so.name}: {elf_dynamic(so)}")


def elf_dynamic(path: pathlib.Path) -> list[tuple[str, str]]:
    import struct
    d = path.read_bytes()
    shoff = struct.unpack_from("<Q", d, 0x28)[0]
    shentsize, shnum, _ = struct.unpack_from("<HHH", d, 0x3A)
    secs = [struct.unpack_from("<IIQQQQIIQQ", d, shoff + i * shentsize) for i in range(shnum)]
    dyn = [s for s in secs if s[1] == 6]
    if not dyn:
        return []
    strtab = secs[dyn[0][6]]
    names = {1: "NEEDED", 14: "SONAME", 29: "RUNPATH"}
    out, off = [], dyn[0][4]
    while True:
        tag, val = struct.unpack_from("<qQ", d, off)
        off += 16
        if tag == 0:
            break
        if tag in names:
            o = strtab[4] + val
            out.append((names[tag], d[o : d.index(b"\0", o)].decode()))
    return out


def rename_dynstr(path: pathlib.Path, old: bytes, new: bytes):
    assert len(old) == len(new)
    data = path.read_bytes()
    if old in data:
        path.write_bytes(data.replace(old, new))
        print(f"  Patched {path.name}: {old!r} -> {new!r}")


if __name__ == "__main__":
    main()
