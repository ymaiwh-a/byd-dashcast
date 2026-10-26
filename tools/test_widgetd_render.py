import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]


def run(root, output, sanitize=True):
    root = Path(root).resolve()
    output = Path(output).resolve()
    source = root / "phase1/qnx/widgetd"
    if not output.is_relative_to(root / "build") or output.exists():
        raise ValueError("Use a NEW directory under this repository's build directory")
    headers = [source / "widgetd.h", source / "wd_canvas.h", source / "wd_font.h",
               source / "wd_icons.h", source / "wd_render.h", source / "wd_screen.h"]
    units = [source / "widgetd.c", source / "wd_canvas.c", source / "wd_font.c",
             source / "wd_font_fake.c", source / "wd_font_freetype.c", source / "wd_render.c",
             source / "wd_screen.c", source / "wd_icons.c", source / "tests/wd_render_test.c"]
    for path in headers + units:
        if path.is_symlink() or not path.is_file() or not path.resolve().is_relative_to(root):
            raise ValueError("Missing, linked or escaped widgetd render source")
    output.mkdir(parents=True)
    executable = output / "wd_render_test"
    compiler = os.environ.get("CC", "cc")
    command = [compiler, "-std=c99", "-Wall", "-Wextra", "-Werror", "-Wconversion", "-Wshadow",
               "-Wstrict-prototypes", "-Wmissing-prototypes", "-pedantic", "-g", "-O1",
               "-fno-omit-frame-pointer"]
    if sanitize:
        command += ["-fsanitize=address,undefined", "-fno-sanitize-recover=all"]
    command += ["-I", str(source)]
    command += [str(p) for p in units]
    command += ["-lm", "-o", str(executable)]
    environment = dict(os.environ, ASAN_OPTIONS="detect_leaks=1:halt_on_error=1",
                       UBSAN_OPTIONS="halt_on_error=1:print_stacktrace=1")
    build = subprocess.run(command, check=True, text=True, capture_output=True, timeout=180,
                           env=environment)
    test = subprocess.run([str(executable)], check=True, text=True, capture_output=True,
                          timeout=60, env=environment, cwd=str(output))
    names = re.findall(r"^PASS ([a-z0-9_]+)$", test.stdout, re.MULTILINE)
    count = re.search(r"^TESTS_PASSED=(\d+)$", test.stdout, re.MULTILINE)
    if count is None or len(names) != int(count[1]) or len(names) != len(set(names)) or len(names) < 12:
        raise ValueError("Incomplete or duplicate widgetd render test results")
    if build.stderr or test.stderr:
        raise ValueError("Review unexpected compiler or runtime diagnostics")
    report = dict(status="PASS_HOST_WIDGETD_RENDER", tests=len(names), cases=names,
                  sanitizer_enabled=sanitize, host_only=True,
                  sources={str(p.relative_to(root)): hashlib.sha256(p.read_bytes()).hexdigest()
                           for p in headers + units},
                  qnx_compiled=False, screen_backend_implemented=True,
                  display_adapter_implemented=False, vehicle_tested=False)
    (output / "REPORT.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report, indent=2))
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--no-sanitize", action="store_true")
    args = parser.parse_args()
    run(args.root, args.output, not args.no_sanitize)
