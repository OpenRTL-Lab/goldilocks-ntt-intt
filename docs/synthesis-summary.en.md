<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks NTT/INTT Hardware Accelerator
Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
File: docs/synthesis-summary.en.md

SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Synthesis results

[简体中文](synthesis-summary.md)

The single-butterfly N65536 configuration passes setup using Design Compiler NXT, TSMC 28 nm HPC+ SSG at 0.81 V / −40 °C, 500 MHz, and 10% setup clock uncertainty. Worst negative slack (WNS, the minimum setup slack) is +0.000022173 ns and total negative slack (TNS, the sum of negative setup slacks) is zero, and there are no violating endpoints. Of 21,281 reported worst-endpoint paths, 99.5207% have strictly fewer than 50 Z/ZN logic levels.

## Design and conditions

| Item | Condition |
| --- | --- |
| Product | Goldilocks NTT/INTT, v1.1.0, lane1, N65536 |
| Top | AsicGoldilocksNttPair, complete product without environments |
| Software | Synopsys Design Compiler NXT X-2025.06-SP3 |
| Standard-cell technology | TSMC 28 nm HPC+, 7-track cells |
| Corner | SSG, 0.81 V, −40 °C |
| Clock | 2.000 ns period, 500 MHz |
| Setup clock uncertainty | 0.200 ns, 10% of period |
| Maximum IO delays | Input/output: 1.000 ns each |
| Output load | 0.020 pF |
| Maximum transition/fanout | 0.250 ns / 32 |
| Storage | Synchronous 1R1W SRAM mapping, 192 physical macros |
| Area basis | Synthesized cell area, including mapped SRAM macros |

SRAM and standard cells use the configured worst-corner libraries for this run. Clock uncertainty is a setup budget, not a change to circuit function. Public SV uses generic synchronous-memory behavior; simulation needs no technology libraries. ASIC integrators connect their macros to the port and one-cycle read contract in [Architecture](architecture.en.md).

## Setup and logic depth

| Metric | Result |
| --- | ---: |
| Setup | Pass |
| WNS | +0.000022173 ns |
| TNS (negative slack sum) | 0 ns |
| Violating endpoints | 0 |
| Constrained, reported setup endpoints | 21,281 |
| Logic levels strictly <50 | 21,179 paths, 99.5207% |
| Exactly 50 | 10 paths |
| Strictly >50 | 92 paths, 0.4323% |
| Maximum depth | 67, two paths |

Statistics use one worst max-data path per constrained setup endpoint: 20,442 internal-to-internal, 235 internal-to-IO, and 604 IO-to-internal. Only data-path pins whose leaf names are exactly Z or ZN count as one level each. Text lines, nets, clock-to-Q, and clock-tree points are excluded. Exact Z/ZN sequences were cross-checked against live timing points; SRAM endpoints are distinguished by full pin names.

## Area and resources

| Disjoint category | Area (µm²) | Fraction of total cell area |
| --- | ---: | ---: |
| Combinational excluding buffers/inverters | 13,414.926 | 0.1892% |
| Buffers/inverters | 905.716 | 0.0128% |
| Non-combinational, including registers | 7,561.288 | 0.1067% |
| SRAM macros | 7,067,731.500 | 99.6914% |
| Total | **7,089,613.430** | **100%** |

Total cell area is **7.089613 mm²**, excluding routing and placement whitespace; it is not die area. The 192 physical SRAM macros implement five logical memories totaling 1.5 MiB. Butterfly hierarchical area is 12,341.140 µm², including its 8,916.432 µm² multiplier. These nested figures are not added again to the disjoint table.

This document summarizes archived synthesis data without distributing original commercial-tool logs. We apologize that technology/SRAM libraries, original DC Tcl, constraints, reports, and snapshots cannot be shared due to licensing and confidentiality requirements. Circuit generation and functional/performance simulation are reproducible from public files; measured cycles and 500 MHz conversions are in [Performance](performance.en.md).
