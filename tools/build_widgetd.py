import argparse
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
UNITS = ["widgetd.c", "wd_canvas.c", "wd_font.c", "wd_font_fake.c", "wd_font_freetype.c",
         "wd_render.c", "wd_screen.c", "wd_icons.c", "widgetd_main.c"]


def build(root, output, variant):
    root = Path(root).resolve()
    output = Path(output).resolve()
    if not output.is_relative_to(root / "build") or output.exists():
        raise ValueError("Use a NEW directory under this repository's build directory")
    source = root / "phase1/qnx/widgetd"
    for name in UNITS:
        path = source / name
        if path.is_symlink() or not path.is_file() or not path.resolve().is_relative_to(root):
            raise ValueError("Missing, linked or escaped widgetd source")
    qnx_host = os.environ.get("QNX_HOST")
    qnx_target = os.environ.get("QNX_TARGET")
    if not qnx_host or not qnx_target:
        print("SKIP_NO_QNX_SDK")
        print("widgetd requires a QNX SDP installation with QNX_HOST and QNX_TARGET exported")
        print("and target libraries libscreen.so and libfreetype.so available in QNX_TARGET")
        return 0
    compilers = sorted(Path(qnx_host).glob("*/usr/bin/qcc*"))
    if not compilers:
        print("SKIP_NO_QNX_SDK")
        print("qcc was not found under QNX_HOST")
        return 0
    compiler = str(compilers[-1])
    output.mkdir(parents=True)
    executable = output / "widgetd"
    command = [compiler, "-V" + variant, "-std=c99", "-O2", "-g", "-Wall", "-Wextra", "-Werror",
               "-I", str(source)]
    command += [str(source / name) for name in UNITS]
    command += ["-lscreen", "-lfreetype", "-lm", "-o", str(executable)]
    subprocess.run(command, check=True, text=True, capture_output=True, timeout=600)
    print("QNX_COMPILED " + str(executable))
    return 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--variant", default=os.environ.get("QNX_VARIANT", "gcc_ntoaarch64le"))
    args = parser.parse_args()
    sys.exit(build(args.root, args.output, args.variant))
