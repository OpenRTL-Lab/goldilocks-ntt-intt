#!/usr/bin/env python3
# OpenRTL-Header-Begin
# OpenRTL — Open-source hardware accelerators.
# Organization: https://github.com/OpenRTL-Lab
# Website: https://openrtl.top/
#
# Project: Goldilocks NTT/INTT Hardware Accelerator
# Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
# Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
# File: tools/source_headers.py
#
# SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
# Copyright applies to original project contributions only.
# SPDX-License-Identifier: MIT
# See LICENSE and applicable third-party notices.
# OpenRTL-Header-End

"""OpenRTL public-repository header helper template; configuration injected at export."""
import argparse
import os
from pathlib import Path
import re

PROJECT = 'Goldilocks NTT/INTT Hardware Accelerator'
DESCRIPTION = 'Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.'
REPOSITORY = 'goldilocks-ntt-intt'
COPYRIGHT = '2026 Goldilocks NTT/INTT contributors'
COVERAGE = {'.gitignore': 'hash', 'LICENSE': 'notice', 'README.zh-CN.md': 'html', 'README.md': 'html', 'THIRD_PARTY_NOTICES.en.md': 'html', 'THIRD_PARTY_NOTICES.md': 'html', 'VERSION': 'sidecar', 'build.mill': 'block', 'common/goldilocks/src/main/scala/ntt/GoldilocksReference.scala': 'block', 'docs/architecture.en.md': 'html', 'docs/architecture.md': 'html', 'docs/interface.en.md': 'html', 'docs/interface.md': 'html', 'docs/journal.en.md': 'html', 'docs/journal.md': 'html', 'docs/mathematical-model.en.md': 'html', 'docs/mathematical-model.md': 'html', 'docs/performance.en.md': 'html', 'docs/performance.md': 'html', 'docs/publication-checklist.en.md': 'html', 'docs/publication-checklist.md': 'html', 'docs/reproduction.en.md': 'html', 'docs/reproduction.md': 'html', 'docs/source-headers.en.md': 'html', 'docs/source-headers.md': 'html', 'docs/synthesis-summary.en.md': 'html', 'docs/synthesis-summary.md': 'html', 'docs/toolchain.en.md': 'html', 'docs/toolchain.md': 'html', 'operators/goldilocks-ntt/cmodel/asic_ntt_reference.c': 'block', 'operators/goldilocks-ntt/reference/LICENSE-MIT': 'notice', 'operators/goldilocks-ntt/sim/asic_ntt_pair_sim.cpp': 'block', 'operators/goldilocks-ntt/sim/native_performance.cpp': 'block', 'operators/goldilocks-ntt/src/main/scala/ntt/asic/AsicGoldilocksArithmetic.scala': 'block', 'operators/goldilocks-ntt/src/main/scala/ntt/asic/AsicNttPair.scala': 'block', 'operators/goldilocks-ntt/src/main/scala/ntt/asic/AsicNttPairEnv.scala': 'block', 'operators/goldilocks-ntt/src/main/scala/ntt/asic/AsicNttResetEnv.scala': 'block', 'operators/goldilocks-ntt/src/tool/scala/ntt/tool/EmitAsicNttPair.scala': 'block', 'operators/goldilocks-ntt/tools/check_reference.py': 'hash', 'rtl/v1.1.0/lane1-n65536/AsicGoldilocksButterfly.sv': 'block', 'rtl/v1.1.0/lane1-n65536/AsicGoldilocksMul.sv': 'block', 'rtl/v1.1.0/lane1-n65536/AsicGoldilocksNttPair.sv': 'block', 'rtl/v1.1.0/lane1-n65536/AsicNttPlane.sv': 'block', 'rtl/v1.1.0/lane1-n65536/AsicSram1R1W_32768x64.sv': 'block', 'rtl/v1.1.0/lane1-n65536/AsicSram1R1W_65536x64.sv': 'block', 'rtl/v1.1.0/lane1-n65536/Queue1_AsicNttTask.sv': 'block', 'rtl/v1.1.0/lane1-n65536/Queue2_AsicNttDataBeat.sv': 'block', 'rtl/v1.1.0/lane1-n65536/Queue2_AsicNttTableBeat.sv': 'block', 'rtl/v1.1.0/lane1-n65536/Queue4_AsicNttDataBeat.sv': 'block', 'rtl/v1.1.0/lane1-n65536/files.f': 'sidecar', 'rtl/v1.1.0/lane1-n65536/memory_32768x64.sv': 'block', 'rtl/v1.1.0/lane1-n65536/memory_65536x64.sv': 'block', 'rtl/v1.1.0/lane1-n65536/ram_2x129.sv': 'block', 'rtl/v1.1.0/lane1-n65536/ram_2x66.sv': 'block', 'rtl/v1.1.0/lane1-n65536/ram_4x129.sv': 'block', 'tools/emit_sv.py': 'hash', 'tools/public_audit.py': 'hash', 'tools/source_headers.py': 'hash', 'tools/test_source_headers.py': 'hash', 'tools/verify.py': 'hash'}
ATTRIBUTION = {}
GENERATED_ATTRIBUTION = ''

ROOT = Path(__file__).resolve().parents[1]
BEGIN = 'OpenRTL-Header-Begin'
END = 'OpenRTL-Header-End'
IGNORED = {'.git', 'build', 'out', '__pycache__', '.bsp', '.bloop', '.metals', '.cache', '.private', '.work', '.internal', '.publication'}


def prefix_length(text):
    """Keep interpreter, encoding and Mill launcher directives at their original positions."""
    offset = 0
    for index, line in enumerate(text.splitlines(keepends=True)):
        if ((index == 0 and line.startswith('#!'))
                or (index < 2 and re.match(r'#.*coding[:=]\s*[-\w.]+', line))
                or line.startswith('//|')):
            offset += len(line)
        else:
            break
    return offset


def header(name, style, attribution=None, generated=False):
    attribution = ATTRIBUTION.get(name, '') if attribution is None else attribution
    lines = [BEGIN, 'OpenRTL — Open-source hardware accelerators.',
             'Organization: https://github.com/OpenRTL-Lab',
             'Website: https://openrtl.top/', '', 'Project: ' + PROJECT,
             DESCRIPTION, 'Repository: https://github.com/OpenRTL-Lab/' + REPOSITORY,
             'File: ' + name, '']
    foreign = COVERAGE.get(name) == 'upstream'
    if not foreign:
        lines += ['SPDX-FileCopyrightText: ' + COPYRIGHT,
                  'Copyright applies to original project contributions only.']
    if attribution:
        lines += attribution.splitlines()
    if generated:
        lines += ['Generated from project inputs using Chisel/CIRCT;',
                  'tool-generated support structures and original notices are retained.']
    lines += ['SPDX-License-Identifier: MIT', 'See LICENSE and applicable third-party notices.', END]
    if style == 'block':
        return '/*\n' + ''.join(' *' + (' ' + line if line else '') + '\n' for line in lines) + ' */\n\n'
    if style == 'hash':
        return ''.join('#' + (' ' + line if line else '') + '\n' for line in lines) + '\n'
    if style == 'html':
        return '<!--\n' + '\n'.join(lines) + '\n-->\n\n'
    if style == 'plain':
        return '\n'.join(lines) + '\n'
    raise ValueError('Unsupported header style')


def add_header(text, name, style, attribution=None, generated=False):
    expected = header(name, style, attribution, generated)
    offset = prefix_length(text)
    if text[offset:].startswith(expected):
        return text
    if text[offset:].startswith(('/*\n * ' + BEGIN, '<!--\n' + BEGIN, '# ' + BEGIN + '\n')):
        raise ValueError('Existing header differs; review it instead of duplicating or replacing it')
    return text[:offset] + expected + text[offset:]


def generated_attribution():
    return GENERATED_ATTRIBUTION


def stamp_generated(directory):
    """Stamp only fresh build RTL; never touch bundled historical releases here."""
    directory = Path(directory)
    if directory.is_symlink() or not directory.resolve().is_relative_to((ROOT / 'build').resolve()):
        raise ValueError('Generated headers are restricted to the build directory')
    paths = sorted(directory.rglob('*.sv'))
    for path in paths:
        if path.is_symlink() or not path.resolve().is_relative_to(directory.resolve()):
            raise ValueError('Generated RTL must not traverse symlinks')
        before = path.read_text(encoding='utf-8')
        after = add_header(before, path.name, 'block', generated_attribution(), True)
        if after != before:
            path.write_text(after, encoding='utf-8')
    return len(paths)


def public_files(root):
    result = set()
    for folder, directories, names in os.walk(root, followlinks=False):
        directories[:] = [n for n in directories if n not in IGNORED and not n.startswith('.mill-')]
        for name in directories + names:
            path = Path(folder) / name
            rel = path.relative_to(root).as_posix()
            if path.is_symlink():
                raise ValueError('Public tree contains a symlink: ' + rel)
            if path.is_file():
                result.add(rel)
    return result


def check(root=ROOT):
    expected = set(COVERAGE)
    expected |= {name + '.license' for name, mode in COVERAGE.items() if mode in ('sidecar', 'upstream')}
    found = public_files(root)
    if found != expected:
        difference = sorted(found ^ expected)
        raise ValueError('Unclassified or missing files (' + str(len(difference)) + '): ' + repr(difference[:20]))
    counts = {'inline': 0, 'sidecar': 0, 'upstream': 0, 'license_notice': 0}
    for name, mode in COVERAGE.items():
        path = root / name
        if mode == 'notice':
            counts['license_notice'] += 1
            continue
        if mode in ('sidecar', 'upstream'):
            if (root / (name + '.license')).read_text(encoding='utf-8') != header(name, 'plain'):
                raise ValueError('Incorrect companion notice: ' + name)
            counts[mode] += 1
            continue
        text = path.read_text(encoding='utf-8')
        offset = prefix_length(text)
        expected_header = header(name, mode, generated=name.endswith('.sv'))
        markers = re.findall(r'(?m)^(?: \* |# )?OpenRTL-Header-Begin$', text)
        if not text[offset:].startswith(expected_header) or len(markers) != 1:
            raise ValueError('Missing, changed or duplicated header: ' + name)
        counts['inline'] += 1
    return counts


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.parse_args()
    try:
        print('Header coverage passed:', check())
    except (OSError, ValueError) as error:
        parser.exit(1, str(error) + '\n')
