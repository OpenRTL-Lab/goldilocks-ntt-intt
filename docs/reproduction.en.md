<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks NTT/INTT Hardware Accelerator
Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
File: docs/reproduction.en.md

SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Reproduction

[简体中文](reproduction.md)

This workflow regenerates the product and environments from public Chisel, checks reference vectors using C and independent Python big-integer models, compares RTL outputs word for word, and verifies bundled SV, protocol recovery, and ideal-interface latency/throughput. All test outputs go into a selected fresh build subdirectory.

## Tools and working directory

First install prerequisites and check versions as described in [Toolchain](toolchain.en.md). After extracting the source archive or cloning the repository, open its root containing `build.mill`. Run every command below there; no other project, commercial EDA, or technology library is needed.

Versioned source and the complete archive are available at [Release v1.1.0](https://github.com/OpenRTL-Lab/goldilocks-ntt-intt/releases/tag/v1.1.0). With Git, clone that release tag to obtain the documented configuration:

```bash
git clone --branch v1.1.0 --depth 1 https://github.com/OpenRTL-Lab/goldilocks-ntt-intt.git
cd goldilocks-ntt-intt
```

The version tag identifies the current source tree, including source notices and documentation. Release downloads provide hardware sources and bundled RTL for this version. Archive annotations and documentation can differ from the online tree. For RTL integration, use the product file list rather than verification environments; use the verification and synthesis documents for supported configurations and measurement conditions.

`--logn m` selects N = 2^m points. The generated functional test uses ten reference frames per size; `--tasks` selects the separate native performance workload (32 tasks per mode by default), not the number of functional reference frames.

## Complete verification

This command generates five representative sizes (16, 64, 1024, 16384, and 65536 points), runs forward/inverse comparisons and native performance tests, exercises small-size protocol/reset/negative controls, and rechecks bundled N65536 SV:

```bash
python3 tools/verify.py --out build/verify --jobs 8
```

`--jobs` controls Verilator compilation parallelism and may be reduced to 2. `--out` must be a new relative directory under build; existing output is never deleted. A successful `results.json` has status=0, with pass=1 and mismatches=0 for positive tests. The negative test's pass=0 and mismatches=1 confirm detection of the intentional fault and are required for overall success.

To reproduce all 13 supported sizes reported in [Performance](performance.en.md), run:

```bash
python3 tools/verify.py --out build/verify-all --logn 4 5 6 7 8 9 10 11 12 13 14 15 16 --tasks 32 --jobs 8
```

## Product-only generation

This command generates the 65,536-point product SV without reference ROMs or file initialization:

```bash
python3 tools/emit_sv.py --out build/emit --logn 16
```

Select another `--logn` in 4..16 for a different size. The output `rtl/product.f` lists product sources, with top module `AsicGoldilocksNttPair`. File lists use repository-relative paths and are invoked from the root.

To lint bundled product syntax, widths, and assertions, run:

```bash
verilator --lint-only --assert --top-module AsicGoldilocksNttPair -f rtl/v1.1.0/lane1-n65536/files.f
```

## Test chain and interpretation

The independent C model implements modular arithmetic and DIT NTT/INTT, audits boundaries and root orders, checks round trips, and cross-checks N≤64 with direct DFT. Python uses arbitrary-precision integers to compare every C input, output, and table entry without invoking the DUT. The generated Chisel environment drives native interfaces and checks outputs; its C++ clock driver only triggers execution and summarizes results.

A separate native performance driver preloads vectors and directly stimulates product interfaces. Both continuous and isolated modes check every result. The performance object records edge-based latencies, the steady window, and the task interval. For N65536, bundled and regenerated products are separately compiled, comparing module sets, circuit text excluding comments/whitespace, and both runtime results.

Per-size c-compile/c-reference/python-reference/emit/lint, generated, and native logs and object directories are retained under the output directory. Protocol tests cover backpressure, unconfigured directions, malformed frames, and invalid tables. Reset tests interrupt issue, stage drain, blocked output, and blocked completion, then verify recovery. On failure, inspect the corresponding log and retry with a new output directory after fixing tools, preserving diagnostics.

Finally, check documentation links, bilingual pairs, and public payload boundaries:

```bash
python3 tools/public_audit.py
```

Build/out caches are not public files and are excluded by the checker. Synthesis conditions and summarized results are in [Synthesis results](synthesis-summary.en.md).
