<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks NTT/INTT Hardware Accelerator
Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
File: docs/publication-checklist.en.md

SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Delivery checklist

[简体中文](publication-checklist.md)

This repository delivers an independent hardware IP: product SV matches Chisel regeneration, functional and ideal-performance tests run independently, authored documents have Chinese/English pairs, synthesis is summarized through conditions and results, and the release archive contains public repository files without machine or internal execution data.

## Contents

- Product, references, elaboration entry, verification environments, and native performance driver are included.
- Bundled configuration is one butterfly lane with N65536; other sizes can be generated through Chisel parameters.
- Environments are excluded from product lists; generic memories instantiate no FPGA or proprietary SRAM primitives.
- README, architecture, interface, model, reproduction, performance, toolchain, synthesis summary, and notices have language counterparts.
- English README links to English documents; captions for the same target are consistent within each language.

## Independent verification

Functional, isolated, and continuous-task tests pass for every logN=4..16. Protocol, reset, and intentional one-bit negative controls behave as expected. Bundled N65536 product circuit text matches regeneration after source-location/whitespace removal, and separate simulation results match. Procedures and pass conditions are in [Reproduction](reproduction.en.md).

## Public payload review

The checker enforces a file whitelist and scans for absolute machine paths, credential patterns, private keys, internal runtime identifiers, provenance digests, and source-location comments. It checks language pairs and link targets. Logs, objects, caches, and version-control management data are not public payload. Moduli, roots, test seeds, and field values in source are mathematical parameters or test data, not credentials.

## Release format

The [Version](../VERSION) appears in the commit message. The GitHub Release attachment packages the complete public repository, including design, SV, simulation, and documents, excluding the Git database and build outputs. Synthesis is provided through [Synthesis results](synthesis-summary.en.md), without internal archives.
