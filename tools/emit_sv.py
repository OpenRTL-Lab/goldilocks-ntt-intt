#!/usr/bin/env python3
# OpenRTL-Header-Begin
# OpenRTL — Open-source hardware accelerators.
# Organization: https://github.com/OpenRTL-Lab
# Website: https://openrtl.top/
#
# Project: Goldilocks NTT/INTT Hardware Accelerator
# Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
# Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
# File: tools/emit_sv.py
#
# SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
# Copyright applies to original project contributions only.
# SPDX-License-Identifier: MIT
# See LICENSE and applicable third-party notices.
# OpenRTL-Header-End

"""Portable Chisel elaboration and exact product dependency closure."""
from source_headers import stamp_generated
import argparse
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
OP = Path('operators/goldilocks-ntt')
TOP = 'AsicGoldilocksNttPair'
BUNDLED = Path('rtl/v1.1.0/lane1-n65536')


def fresh(value):
    path = Path(value)
    if path.is_absolute() or '..' in path.parts or not path.parts or path.parts[0] != 'build':
        raise ValueError('Use a fresh relative output directory under build')
    if path.exists():
        raise ValueError('Output exists; choose another directory')
    path.mkdir(parents=True)
    return path


def run(command, out, label, expected=0):
    with (out / (label + '.log')).open('x') as log:
        result = subprocess.run([str(x) for x in command], cwd=ROOT,
                                stdout=log, stderr=subprocess.STDOUT)
    if result.returncode != expected:
        raise RuntimeError(f'{label} failed; inspect its log in the output directory')


def code(text):
    return re.sub(r'/\*.*?\*/|//[^\n]*', '', text, flags=re.S)


def product_closure(rtl):
    stamp_generated(rtl)
    definitions, texts = {}, {}
    for path in sorted(rtl.glob('*.sv')):
        text = re.sub(r'[ \t]*//[^\n]*\.scala:\d+:[^\n]*', '', path.read_text())
        path.write_text(text)
        texts[path] = code(text)
        for module in re.findall(r'\bmodule\s+(\w+)', texts[path]):
            assert module not in definitions, 'Duplicate module'
            definitions[module] = path
    selected, pending = set(), [TOP]
    while pending:
        path = definitions[pending.pop()]
        if path in selected:
            continue
        selected.add(path)
        instances = set(re.findall(
            r'^[ \t]*(\w+)\b[ \t]+(?:#\s*\([^;]*?\)\s*)?\w+\s*\(',
            texts[path], re.M | re.S))
        pending.extend(instances & definitions.keys())
    for path in selected:
        stripped = re.sub(r'"(?:[^"\\]|\\.)*"', '""', texts[path])
        assert not re.search(r'\$readmem|%|\b(?:DSP48\w*|RAMB\w*|URAM\w*|BUFG\w*|IOBUF)\b', stripped)
        assert 'SimTop' not in stripped
    return sorted(selected)


def filelist(sources, target):
    target.write_text(''.join('+incdir+' + str(p) + '\n' for p in sorted({p.parent for p in sources}))
                      + ''.join(str(p) + '\n' for p in sorted(sources)))


def generate(value, logn=16, mode='product'):
    os.chdir(ROOT)
    if not 4 <= logn <= 16:
        raise ValueError('logN must be 4..16')
    out = fresh(value)
    vectors = out / 'vectors'
    if mode != 'product':
        run(shlex.split(os.environ.get('CC', 'cc')) + ['-std=c11', '-O2', '-Wall', '-Wextra', '-Werror',
            OP / 'cmodel/asic_ntt_reference.c', '-o', out / 'reference'], out, 'c-compile')
        run([out / 'reference', vectors, logn], out, 'c-reference')
        run([sys.executable, OP / 'tools/check_reference.py', vectors, logn], out, 'python-reference')
    rtl = out / 'rtl'
    run(shlex.split(os.environ.get('MILL', 'mill')) + ['-i', 'goldilocksNtt.tool.runMain',
        'ntt.tool.EmitAsicNttPair', rtl, logn, vectors, mode], out, 'emit')
    filelist(product_closure(rtl), rtl / 'product.f')
    filelist(list(rtl.glob('*.sv')), rtl / 'simulation.f')
    (out / 'configuration.json').write_text(json.dumps(
        {'version': '1.1.0', 'logN': logn, 'n': 1 << logn, 'lanes': 1, 'mode': mode}, indent=2) + '\n')
    return rtl


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out', default='build/emit')
    parser.add_argument('--logn', type=int, default=16)
    args = parser.parse_args()
    generate(args.out, args.logn)
    print('Product generated: use rtl/product.f in the selected output directory')
