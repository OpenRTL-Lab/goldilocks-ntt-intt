<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks NTT/INTT Hardware Accelerator
Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
File: docs/source-headers.en.md

SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Original-file headers

[简体中文](source-headers.md)

This policy explains OpenRTL organization/project notices, copyright boundaries and complete file coverage, so that source attribution remains traceable when code is copied outside the repository.

## Scope and attribution

Original code and documentation carry a short organization/project/license header. Branding does not transfer copyright: existing project holders are retained. Shared designs authored within this accelerator series remain original implementations, even under a vendor or common directory. Upstream parameter tables retain their source and license; mixed files identify both original contributions and upstream parameters. Canonical license texts are preserved without claiming authorship of their wording.

## Formats and generation

Code uses native comments; Markdown uses an invisible HTML comment. Interpreter, encoding and Mill launcher directives remain first. VERSION, JSON, compiler file lists and preserved parameter-only tables use adjacent .license files; keep companions when copying those files. Generated SV retains its CIRCT identification and acknowledges tool-generated support structures. The supported Python emission entry stamps new build RTL automatically; direct Scala emission must be followed by the same stamping step.

For a direct Scala emission, run the following from the repository root, replacing `build/emit/rtl` with that emission's RTL directory. This stamps notices only; it does not generate or verify hardware:

```bash
python3 -c "import sys; from pathlib import Path; sys.path.insert(0, 'tools'); from source_headers import stamp_generated; stamp_generated(Path('build/emit/rtl'))"
```

The helper's command-line coverage check below does not add missing headers.

## Coverage checks

Run `python3 tools/source_headers.py` and `python3 tools/test_source_headers.py`. The exact inventory classifies file formats and attribution; unknown files are rejected. These checks cover source notices, not hardware behavior or performance.
