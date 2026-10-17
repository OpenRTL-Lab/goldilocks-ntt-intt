<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks NTT/INTT Hardware Accelerator
Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
File: docs/performance.en.md

SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Performance

[简体中文](performance.md)

With N65536 and one butterfly pipeline, ideal-interface measurements give a task latency of 590,133 cycles, a sustained task interval of 590,134 cycles, a compute phase of 524,592 cycles, and 99.9421% useful butterfly utilization during computation. At 500 MHz these correspond to approximately 847.27 transforms and 55.5264 million field elements per second.

## Metric definitions

A task is one forward or inverse N-point transform. Time is the difference between rising-edge indices t, without counting both boundaries inclusively:

| Metric | Definition | Measured N65536 value |
| --- | --- | ---: |
| Isolated task latency | completion transfer edge − task transfer edge | 590,133 cycles |
| Saturated-request latency | Same per-task difference under continuous requests | 590,133 cycles |
| Data-frame latency | Final output transfer edge − first input transfer edge | 590,130 cycles |
| Compute cycles | completion.computeCycles; Issue/Drain occupation | 524,592 cycles |
| Sustained task interval | Mean difference between steady-state completion edges | 590,134 cycles |
| Sustained throughput | Reciprocal of measured task interval | 1.6945303948×10⁻⁶ tasks/cycle |

The original Chisel environment counts both boundary cycles in frameCycles/taskCycles, yielding 590,131/590,134. Those counters are one cycle greater than the edge differences above. Latency here uses edge differences, while throughput uses successive actual completion edges.

## Method and results

See [Toolchain](toolchain.en.md) and [Reproduction](reproduction.en.md). The native driver preloads independent C vectors, loads both twiddle tables, and waits for configuration before running two modes of 32 tasks each:

1. Isolated mode inserts an idle cycle after the previous completion before the next request, measuring isolated task latency.
2. Saturated mode offers each request as early as possible, supplies consecutive input beats whenever ready, and holds output/completion ready high. Tables are loaded only once, before the timed task window; table-loading cycles are excluded from the task metrics.
3. Tasks cycle through ten reference frames in tag order. Every output word, last flag, direction, tag, and success status is checked: 2,097,152 elements per mode.
4. The steady window spans completion tags 2 through 29, excluding boundary tasks. Its 27 intervals take **15,933,618 cycles**, averaging **590,134 cycles/task**.

Regenerated and bundled SV results match exactly. Functional regression covers all 13 sizes logN=4..16, ten tasks per size: zeros, ones, boundary values, deterministic random, and repeated random, each forward and inverse. All 130 tasks and 1,310,560 output elements match. Additional tests exercise backpressure, malformed requests, and recovery from four mid-task resets. A negative control flips one reference bit and detects exactly one mismatch as expected.

## Frequency conversion

$$
L_{\mathrm{task}}=590133/f,\qquad
R_{\mathrm{task}}=f/590134,\qquad
R_{\mathrm{element}}=65536f/590134.
$$

At $f=500\times10^6$ Hz, latency is **1.180266 ms**, sustained throughput is **847.2652 tasks/s**, and element throughput is **55,526,371.98 elements/s**. These convert measured cycles at the specified clock rate, rather than describing host simulator execution speed, board measurements, or post-silicon performance. Corresponding synthesis conditions are in [Synthesis results](synthesis-summary.en.md).

## Arithmetic units and useful utilization

A busy cycle accepts a valid arithmetic operation; electrical switching alone is not counted. With $m=\log_2 N=16$, each transform launches $mN/2=524288$ butterflies and modular multiplies, with one addition and subtraction per butterfly. Inverse mode additionally applies two modular halves. Compute utilization divides each unit's useful-operation count by 524,592 Issue/Drain cycles; sustained full-task utilization divides it by the measured 590,134-cycle task interval:

| Unit | Count/structure | Valid operations/task | Compute utilization | Sustained full-task utilization |
| --- | --- | ---: | ---: | ---: |
| Modular multiplier | 1, four 32×32 partial products | 524,288 | 99.9421% | 88.8422% |
| Modular add/subtract | 1 each, parallel within butterfly | 524,288 each | 99.9421% each | 88.8422% each |
| Modular half | 2, enabled for inverse | 524,288 each in inverse | 99.9421% each in inverse | 88.8422% each in inverse |

Forward mode bypasses modular halving. These ratios use useful operations, not invalid-cycle switching; drain and input/output cycles enter the respective denominators. The multiplier is the largest arithmetic unit and is highly utilized during computation. SRAM dominates total circuit area; see [Architecture](architecture.en.md) and [Synthesis results](synthesis-summary.en.md).

The butterfly's one-cycle initiation interval does not mean one complete transform per cycle. This configuration sequentially processes complete tasks using two data planes, making task latency and interval similar; both are nevertheless defined and measured separately. Tests use ideal supply and consumption timing, without external bandwidth, latency, or shared-port sweeps.
