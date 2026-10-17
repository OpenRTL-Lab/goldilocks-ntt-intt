<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks NTT/INTT Hardware Accelerator
Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
File: README.md

SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Goldilocks NTT/INTT Hardware Accelerator

[简体中文](README.zh-CN.md)

A standalone **number-theoretic transform (NTT) and inverse NTT hardware accelerator** over the Goldilocks field, written in Chisel with generated SystemVerilog. Use it for polynomial coefficient/evaluation conversion in STARK proving and low-degree-extension pipelines, with native interfaces for ASIC or FPGA integration.

[Release v1.1.0](https://github.com/OpenRTL-Lab/goldilocks-ntt-intt/releases/tag/v1.1.0) · [Native interface](docs/interface.en.md) · [Reproduction](docs/reproduction.en.md) · [Performance](docs/performance.en.md) · [Synthesis results](docs/synthesis-summary.en.md)

It is licensed under the [MIT License](LICENSE), permitting free commercial use, modification, and redistribution, provided the copyright and license notices are retained; see [Third-party notices](THIRD_PARTY_NOTICES.en.md) for parameter sources.

## Computation and task units

One task performs one complete N-point forward NTT or normalized inverse NTT. Field words are unsigned 64-bit canonical representatives, meaning 0 <= x < p for p = 2^64 - 2^32 + 1. A data beat carries two consecutive elements of that one transform; it is not two tasks. The single butterfly pipeline is reused across transform stages, not one entire transform per clock. Twiddle tables are configured before task admission; the published task timings exclude that table-loading phase.

## At a glance

- **Field:** $p=2^{64}-2^{32}+1$; 64-bit canonical field elements.
- **Transforms:** radix-2 NTT and normalized INTT, with natural-order inputs and outputs. The generator supports 16–65,536 points (`logN=4..16`); released SV is the single-butterfly, 65,536-point configuration.
- **Integration:** native valid/ready task, twiddle-table, two-element data, and completion channels. Storage uses generic synchronous one-read/one-write interfaces; map these to your target SRAMs.
- **Included:** Chisel, generated product SV, independent C/Python references, simulation and performance tools, and bilingual documentation.

## Repository contents

- [Chisel design](operators/goldilocks-ntt/src/main/scala/ntt/asic/): product arithmetic, transform scheduling, and independent verification environments.
- [Generated SV](rtl/v1.1.0/lane1-n65536/): complete product RTL for a single butterfly pipeline and 65,536 points.
- [Generation script](tools/emit_sv.py) and [Verification script](tools/verify.py): standalone elaboration, C/Python checks, and RTL simulation.
- [C reference model](operators/goldilocks-ntt/cmodel/asic_ntt_reference.c) and [Native-interface performance driver](operators/goldilocks-ntt/sim/native_performance.cpp): word-for-word comparison and continuous-task measurements.
- [Architecture](docs/architecture.en.md), [Native interface](docs/interface.en.md), and [Toolchain](docs/toolchain.en.md): configuration, pipelining, storage, and integration contracts.

## Applications

Systems requiring polynomial interpolation or batch evaluation over the Goldilocks field can delegate NTT/INTT to this module. Examples include coefficient recovery and evaluation-domain conversion in STARK proving pipelines, or the INTT and NTT steps of low-degree extension. Zero padding and coset scaling can be connected through an external datapath. These examples illustrate integration opportunities rather than limiting the module's applications.

## Performance metrics

Under ideal supply/consumption, RTL measurements for the 65,536-point configuration give **590,133 cycles of task latency** and **590,134 cycles per sustained task**. At an assumed 500 MHz, these cycles convert to **1.180266 ms** and approximately **847.27 transforms/s**; they are not board or silicon measurements. The 99.9421% compute-phase utilization is 524,288 valid butterfly issues divided by 524,592 compute-phase cycles, not whole-task or external-interface utilization. See [Performance](docs/performance.en.md) for timing windows and measurements.

## Mathematical model

The module performs radix-2 NTT/INTT over the field with modulus $2^{64}-2^{32}+1$, using natural-order input and output. INTT includes $1/N$ normalization; roots and twiddle tables are supplied through the native interface. See [Mathematical model](docs/mathematical-model.en.md) for the exact definition.

## Quick reproduction

Download the complete source archive from [Release v1.1.0](https://github.com/OpenRTL-Lab/goldilocks-ntt-intt/releases/tag/v1.1.0), or browse the source and generated-SV directories above. The version tag identifies the current source tree, including source notices and documentation. Release downloads provide hardware sources and bundled RTL for this version. Archive annotations and documentation can differ from the online tree. For RTL integration, use the product file list rather than verification environments; use the verification and synthesis documents for supported configurations and measurement conditions.

First install the versions listed in [Toolchain](docs/toolchain.en.md). From the repository root, generate the N65536 product into a new output directory:

```bash
python3 tools/emit_sv.py --out build/emit --logn 16
```

This generates RTL; it does not run verification. Regenerate RTL from Chisel, compare every output against independent C/Python references, and run bundled-SV, protocol, reset, and performance tests using the full workflow and expected results in [Reproduction](docs/reproduction.en.md). Read [Native interface](docs/interface.en.md) before connecting a host or memory system.

## Verification scope and limitations

The documented functional regression covers all 13 supported sizes, 130 forward/inverse tasks and 1,310,560 output elements with zero differences. It also includes backpressure, malformed inputs, reset/recovery at four execution boundaries, an intentional one-bit reference error, and comparison of bundled and regenerated product SV. See [Reproduction](docs/reproduction.en.md) for commands and pass criteria.

This is a standalone compute block, not a complete STARK prover or coset-LDE engine; padding and coset scaling belong to the surrounding datapath. The delivery does not include a SoC, bus adapter or board project. The DC setup result below is not place-and-route signoff or board validation.

## Synthesis results

This configuration passes setup using Design Compiler NXT with TSMC 28 nm HPC+, SSG 0.81 V / −40 °C, 500 MHz, and 10% setup clock uncertainty. Of the reported worst setup paths per endpoint, 99.52% have fewer than 50 counted logic levels; each data-path cell output pin named Z or ZN counts as one level, excluding nets and clock-tree points. Conditions, area, and path statistics are detailed in [Synthesis results](docs/synthesis-summary.en.md).

## Publication scope

The repository provides Chisel, generated product SV, reproducible generation and simulation, bilingual documentation, and a synthesis-results summary. We apologize that technology and SRAM libraries, original DC Tcl, constraints, reports, and synthesis snapshots cannot be shared due to licensing and confidentiality requirements. The delivered component is an independent operator, without a SoC, bus adapter, or board project.

## OpenRTL and custom integration

Browse [OpenRTL](https://github.com/OpenRTL-Lab) and the [ZK accelerator catalogue](https://github.com/OpenRTL-Lab/zk-accelerators) for related field-arithmetic, LDE, FRI and hashing modules. For custom datapaths, interface adaptation or target-platform integration, use the cooperation/contact entry on the organization profile. MIT use does not require purchasing customization services; do not post confidential specifications in public issues.
