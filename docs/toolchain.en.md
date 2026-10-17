<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks NTT/INTT Hardware Accelerator
Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
File: docs/toolchain.en.md

SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Toolchain

[简体中文](toolchain.md)

Reproduction uses JDK 21, Mill 1.1.7, Scala 2.13.18, Chisel 7.13.0, firtool 1.149.0, Verilator 5.050, Python 3.12, and GCC 16.2.0. Generation and simulation require no commercial EDA, technology library, preinstalled project repository, or machine-specific directories.

## Installation and dependencies

On Linux, install JDK 21, Python 3.12, GNU Make, and GCC/G++ supporting C11 `__uint128_t` and C++17. Install Mill 1.1.7 following the [Mill installation guide](https://mill-build.org/mill/1.1.x/cli/installation-ide.html) and Verilator 5.050 following the [Verilator installation guide](https://verilator.org/guide/latest/install.html). Java, mill, python3, cc, g++, make, and verilator must be available on PATH.

`build.mill` pins Scala, Chisel, and the compiler plugin. The first Mill invocation resolves Maven dependencies and matching firtool. Dependency downloads require network access; prepared dependencies may use your own cache. Scripts require no internal repository or fixed cache location.

Before generation and verification, check tool availability and versions:

```bash
java -version
mill --version
python3 --version
cc --version
g++ --version
make --version
verilator --version
```

## Custom launchers

`MILL` can select a Mill launch command and `CC` a C compiler command. A standard environment needs neither override. If choosing a custom launcher, pass it through environment variables without modifying the product design or putting machine paths in repository files.

Generated files identify the firtool version on their first line. With matching versions, verification checks regenerated product circuit text against bundled product text, ignoring source-location comments and whitespace. Build logs and intermediate files remain local diagnostics, outside release archives. See [Reproduction](reproduction.en.md) for the complete workflow.
