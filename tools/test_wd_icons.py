import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def run(root, output):
    root = Path(root).resolve()
    output = Path(output).resolve()
    source = root / "phase1/qnx/widgetd"
    if not output.is_relative_to(root / "build") or output.exists():
        raise ValueError("Use a NEW directory under this repository's build directory")
    paths = [source / "wd_icons.c", source / "wd_icons.h", source / "tests/wd_icons_test.c"]
    for path in paths:
        if path.is_symlink() or not path.is_file() or not path.resolve().is_relative_to(root):
            raise ValueError("Missing, linked or escaped wd_icons source")
    output.mkdir(parents=True)
    executable = output / "wd_icons_test"
    compiler = os.environ.get("CC", "cc")
    command = [compiler, "-std=c99", "-Wall", "-Wextra", "-Werror", "-Wconversion", "-Wshadow",
               "-Wstrict-prototypes", "-Wmissing-prototypes", "-pedantic", "-g", "-O1",
               "-I", str(source), str(paths[0]), str(paths[2]), "-o", str(executable)]
    build = subprocess.run(command, check=True, text=True, capture_output=True, timeout=120)
    test = subprocess.run([str(executable)], check=True, text=True, capture_output=True,
                          timeout=60, cwd=str(output))
    names = re.findall(r"^PASS ([a-z0-9_]+)$", test.stdout, re.MULTILINE)
    count = re.search(r"^TESTS_PASSED=(\d+)$", test.stdout, re.MULTILINE)
    if count is None or len(names) != int(count[1]) or len(names) != len(set(names)) or len(names) < 25:
        raise ValueError("Incomplete or duplicate wd_icons test results")
    if build.stderr or test.stderr:
        raise ValueError("Review unexpected compiler or runtime diagnostics")
    golden = (root / "tools/wd_icons.sha256").read_text(encoding="utf-8").strip()
    decoded = output / "decoded.bin"
    digest = hashlib.sha256(decoded.read_bytes()).hexdigest()
    if digest != golden:
        raise ValueError("Decoded icon stream sha256 mismatch")
    header = (source / "wd_icons.h").read_text(encoding="utf-8")
    slot_count = int(re.search(r"#define WD_ICON_SLOTS (\d+)", header)[1])
    if decoded.stat().st_size != slot_count * 96 * 96 * 4:
        raise ValueError("Unexpected decoded byte count")
    report = dict(status="PASS_HOST_WD_ICONS", tests=len(names), cases=names,
                  decoded_sha256=digest,
                  sources={str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
                           for p in paths})
    (output / "REPORT.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    run(args.root, args.output)
