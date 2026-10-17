<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks NTT/INTT Hardware Accelerator
Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
File: THIRD_PARTY_NOTICES.md

SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 第三方声明

[English](THIRD_PARTY_NOTICES.en.md)

本项目采用 MIT 许可证公开自行设计的 Chisel、验证代码和产品 SV；Goldilocks 模数与参考单位根来自公开数学参数，单位根数值与 Proofman 表一致，本文件说明来源、工具依赖和对应许可。

## 数学参数

这里 m 表示单位根的阶为 2^m，参数表也包含低于硬件最小支持变换尺寸的阶数。参考根数值采用 [Proofman 单位根表](https://github.com/0xPolygonHermez/pil2-proofman/blob/main/fields/src/goldilocks.rs) 的 m=0..16 项。该上游提供 MIT／Apache-2.0 许可选项，MIT 版权为 2025 SilentSig Switzerland GmbH；为完整保留声明，随包附 [上游 MIT 许可](operators/goldilocks-ntt/reference/LICENSE-MIT)。

这里只采用数学参数，不携带上游证明系统、软件 FFT 或运行时。C 模型、Python 大整数校验与硬件流水线是本项目独立实现。正逆变换及表内容的严格约定见 [数学模型](docs/mathematical-model.md)。

## 工具依赖

生成依赖 Chisel／CIRCT 和 Mill，仿真依赖 Verilator，参考模型编译依赖 GCC。工具由用户安装并受各自许可证约束，不打包工具可执行文件、依赖缓存或商业 EDA。版本及安装入口见 [工具链说明](docs/toolchain.md)。本项目自身许可见 [MIT 许可证](LICENSE)。
