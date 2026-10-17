#!/usr/bin/env python3
# OpenRTL-Header-Begin
# OpenRTL — Open-source hardware accelerators.
# Organization: https://github.com/OpenRTL-Lab
# Website: https://openrtl.top/
#
# Project: Goldilocks NTT/INTT Hardware Accelerator
# Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
# Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
# File: tools/public_audit.py
#
# SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
# Copyright applies to original project contributions only.
# SPDX-License-Identifier: MIT
# See LICENSE and applicable third-party notices.
# OpenRTL-Header-End

"""审查公开文件；只读，不扫描Git元数据或运行缓存，不自动删文件。"""
from pathlib import Path
import posixpath
import re
import sys
from urllib.parse import unquote, urlsplit

ROOT = Path(__file__).resolve().parents[1]
GENERATED = {'build', 'out', '.bsp', '.metals', '__pycache__'}
ROOT_FILES = {'README.md', 'LICENSE', 'VERSION', '.gitignore', 'build.mill', 'THIRD_PARTY_NOTICES.md'}
ROOT_FILES |= {'README.zh-CN.md', 'THIRD_PARTY_NOTICES.en.md'}
DOCS = {'architecture.md', 'interface.md', 'toolchain.md', 'reproduction.md',
        'performance.md', 'synthesis-summary.md', 'publication-checklist.md', 'mathematical-model.md'}
DOCS |= {name[:-3] + '.en.md' for name in tuple(DOCS)}
SCALA = {'AsicGoldilocksArithmetic.scala', 'AsicNttPair.scala', 'AsicNttPairEnv.scala', 'AsicNttResetEnv.scala'}
RTL = {'AsicGoldilocksButterfly.sv', 'AsicGoldilocksMul.sv', 'AsicGoldilocksNttPair.sv',
       'AsicNttPlane.sv', 'AsicSram1R1W_32768x64.sv', 'AsicSram1R1W_65536x64.sv',
       'Queue1_AsicNttTask.sv', 'Queue2_AsicNttDataBeat.sv', 'Queue2_AsicNttTableBeat.sv',
       'Queue4_AsicNttDataBeat.sv', 'memory_32768x64.sv', 'memory_65536x64.sv',
       'ram_2x129.sv', 'ram_2x66.sv', 'ram_4x129.sv'}
OP = 'operators/goldilocks-ntt/'
EXPECTED = (ROOT_FILES | {'docs/' + x for x in DOCS}
            | {'tools/' + x for x in ('emit_sv.py', 'verify.py', 'public_audit.py')}
            | {OP + 'src/main/scala/ntt/asic/' + x for x in SCALA}
            | {OP + 'src/tool/scala/ntt/tool/EmitAsicNttPair.scala',
               OP + 'cmodel/asic_ntt_reference.c', OP + 'sim/asic_ntt_pair_sim.cpp',
               OP + 'sim/native_performance.cpp', OP + 'tools/check_reference.py',
               'common/goldilocks/src/main/scala/ntt/GoldilocksReference.scala',
               OP + 'reference/LICENSE-MIT'}
            | {'rtl/v1.1.0/lane1-n65536/' + x for x in RTL | {'files.f'}})
EXPECTED |= {'docs/journal.en.md', 'docs/source-headers.md', 'tools/test_source_headers.py', 'VERSION.license', 'docs/source-headers.en.md', 'tools/source_headers.py', 'docs/journal.md', 'rtl/v1.1.0/lane1-n65536/files.f.license'}


def document_link_failures(documents, targets):
    """检查双语配对及相对链接；同一语言内指向同一目标的名称一致。"""
    failures, labels = [], {}
    for name, content in sorted(documents.items()):
        counterpart = ('README.zh-CN.md' if name == 'README.md' else 'README.md' if name == 'README.zh-CN.md'
                       else name[:-6] + '.md' if name.endswith('.en.md') else name[:-3] + '.en.md')
        english = name == 'README.md' or name.endswith('.en.md')
        if counterpart not in documents:
            failures.append(name + '：缺少对应语言版本')
        paragraphs = re.split(r'\n\s*\n', re.sub(r'\A<!--\nOpenRTL-Header-Begin.*?-->\n\n', '', content, count=1, flags=re.S).strip())
        if (len(paragraphs) < 2 or not paragraphs[0].startswith('# ')
                or re.match(r'(?:#|\||```|[-*] |\d+\. )', paragraphs[1])):
            failures.append(name + '：标题后需要概括全文的首段正文')
        for label, target in re.findall(r'(?<!!)\[([^\[\]\n]+)\]\(([^\s)]+)\)', content):
            parts = urlsplit(target)
            if parts.scheme or parts.netloc:
                key = (parts.scheme, parts.netloc, parts.path, parts.query)
            else:
                key = posixpath.normpath(posixpath.join(posixpath.dirname(name),
                    unquote(parts.path))) if parts.path else name
                if key not in targets:
                    failures.append(name + '：链接目标不存在：' + target)
                if label == ('简体中文' if english else 'English') and key == counterpart:
                    continue
                target_english = str(key) == 'README.md' or str(key).endswith('.en.md')
                if english and str(key).endswith('.md') and not target_english:
                    failures.append(name + '：英文文档不得索引中文Markdown：' + target)
                if target_english and re.search(r'[\u4e00-\u9fff]', label):
                    failures.append(name + '：英文阅读入口必须使用英文链接文字：' + target)
            language = 'zh' if re.search(r'[\u4e00-\u9fff]', label) else 'en'
            previous = labels.setdefault((language, key), (label, name))
            if previous[0] != label:
                failures.append(name + '：同一目标链接名称不一致：' + label
                    + '；应与' + previous[1] + '中的“' + previous[0] + '”一致')
    return failures


def main():
    failures, files, ignored = [], set(), set()
    documents, targets = {}, set()
    # 用字符编码构造斜线，避免扫描规则本身包含一个机器目录示例。
    patterns = [
        ('机器绝对目录', r'\x2f(?:home|Users|root|opt|mnt|media|tmp|var|work|workspace|private|[A-Za-z0-9_.-]+-data)\x2f'),
        ('Windows绝对目录', r'\b[A-Z]:\\'),
        ('长十六进制追溯标识', r'\b[0-9a-fA-F]{40,64}\b'),
        ('内部追溯字段', r'\b(?:sha(?:1|256|512)|source[_-]commit|provenance[_-]id|runner[_-]digest)\b'),
        ('疑似凭证', r'(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[A-Z0-9]{16})'),
        ('私钥头', r'BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY'),
        ('IP地址', r'\b(?:\d{1,3}\.){3}\d{1,3}\b'),
        ('内部运行标识', r'\b(?:v\d{2}-[0-9a-f]{12}|agent-[0-9a-f]{16,})\b'),
        ('源码定位注释', r'\.scala:\d+:\d+'),
    ]
    for path in ROOT.rglob('*'):
        rel = path.relative_to(ROOT)
        if rel.parts[0] == '.git':
            continue
        if any(part in GENERATED for part in rel.parts):
            index = next(i for i, part in enumerate(rel.parts) if part in GENERATED)
            ignored.add('/'.join(rel.parts[:index+1]))
            continue
        targets.add(rel.as_posix())
        if path.is_symlink():
            failures.append(str(rel) + '：禁止符号链接')
            continue
        if not path.is_file():
            continue
        name = rel.as_posix()
        files.add(name)
        if name not in EXPECTED:
            failures.append(name + '：不在公开白名单')
        try:
            text = path.read_text(encoding='utf-8')
        except (UnicodeError, OSError):
            failures.append(name + '：不是可读的UTF-8文本')
            continue
        if '\x00' in text:
            failures.append(name + '：包含二进制字节')
        for label, pattern in patterns:
            if re.search(pattern, text):
                failures.append(name + '：' + label)
        if path.suffix == '.md':
            documents[name] = text
    failures.extend(name + '：缺少必要文件' for name in sorted(EXPECTED - files))
    failures.extend(document_link_failures(documents, targets))
    lines = (ROOT / 'rtl/v1.1.0/lane1-n65536/files.f').read_text().splitlines()
    assert {line for line in lines if not line.startswith('+incdir+')} == {
        'rtl/v1.1.0/lane1-n65536/' + name for name in RTL}, '产品列表错误'
    if failures:
        print('\n'.join(failures))
        return 1
    print(f'公开载荷审查通过：{len(files)}个文本文件；无意外文件或疑似机器/凭证/追溯信息，文档链接有效且名称一致。')
    if ignored:
        print('注意：运行缓存被排除，不允许公开提交；本次存在的排除目录：' + ', '.join(sorted(ignored)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
