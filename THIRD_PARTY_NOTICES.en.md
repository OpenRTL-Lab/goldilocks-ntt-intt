<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks NTT/INTT Hardware Accelerator
Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
File: THIRD_PARTY_NOTICES.en.md

SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Third-party notices

[简体中文](THIRD_PARTY_NOTICES.md)

This project releases its independently designed Chisel, verification code, and product SV under MIT. The Goldilocks modulus and reference roots are public mathematical parameters; root values match Proofman's table. This document records attribution, tool dependencies, and relevant licensing.

## Mathematical parameters

Here m indexes roots of order 2^m; the parameter table includes orders below the hardware's supported minimum transform size. Reference roots use entries m=0..16 of the [Proofman root table](https://github.com/0xPolygonHermez/pil2-proofman/blob/main/fields/src/goldilocks.rs). Upstream offers MIT/Apache-2.0 options, with MIT copyright 2025 SilentSig Switzerland GmbH. The [Upstream MIT license](operators/goldilocks-ntt/reference/LICENSE-MIT) is included to preserve the notice.

Only mathematical parameters are used; no upstream proving system, software FFT, or runtime is bundled. The C model, Python big-integer checks, and hardware pipeline are independently implemented here. Exact transform and table contracts are in [Mathematical model](docs/mathematical-model.en.md).

## Tool dependencies

Generation uses Chisel/CIRCT and Mill; simulation uses Verilator; reference compilation uses GCC. Users install tools under their respective licenses. Executables, dependency caches, and commercial EDA are not bundled. Versions and installation links are in [Toolchain](docs/toolchain.en.md). Project licensing is in the [MIT License](LICENSE).
