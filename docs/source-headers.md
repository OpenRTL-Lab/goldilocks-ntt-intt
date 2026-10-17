<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks NTT/INTT Hardware Accelerator
Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
File: docs/source-headers.md

SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 原创文件Header规范

[English](source-headers.en.md)

本文说明OpenRTL组织与项目标识、版权边界和逐文件覆盖规则，确保代码脱离仓库后仍可追溯来源。

## 来源与归属

原创代码和文档使用简短组织／项目／许可Header。品牌展示不等于版权转让，现有持有人保持不变。本加速器系列原创的共享硬件即使位于common或vendor目录也不误归上游；上游参数表保留来源及许可，混合文件同时标明原创部分和参数归属。标准许可证原文不改，也不对其措辞主张原创。

## 格式与生成

代码用原生注释，Markdown用不可见HTML注释；解释器、编码及Mill启动指令保持首部。VERSION、JSON、编译清单及保留的纯参数表使用相邻.license文件，单独复制时也须携带伴随文件。生成SV保留CIRCT标识，并注明工具生成的支持结构。受支持的Python生成入口自动为新build RTL加Header；直接调用Scala入口后仍须执行同一添加步骤。

若直接使用 Scala 生成，在仓库根目录执行以下命令，并将 `build/emit/rtl` 替换为该次生成的 RTL 目录。这只添加声明，不生成或验证硬件：

```bash
python3 -c "import sys; from pathlib import Path; sys.path.insert(0, 'tools'); from source_headers import stamp_generated; stamp_generated(Path('build/emit/rtl'))"
```

下面的命令行覆盖检查只检查声明，不添加缺失 Header。

## 覆盖检查

运行 `python3 tools/source_headers.py` 和 `python3 tools/test_source_headers.py`。精确清单说明文件格式与归属，未知文件被拒绝。检查范围是来源声明，不是硬件行为或性能。
