<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks NTT/INTT Hardware Accelerator
Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
File: docs/journal.en.md

SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Current release reference

[简体中文](journal.md)

Goldilocks NTT/INTT Hardware Accelerator, version 1.1.0. Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.

## Deliverables and use

The repository supplies Chisel sources, generated product SystemVerilog, native-interface documentation, mathematical references and reproduction tools. Use the [Product file list](../rtl/v1.1.0/lane1-n65536/files.f) for circuit integration, excluding verification environments. Start with [Native interface](interface.en.md), then [Reproduction](reproduction.en.md), [Performance](performance.en.md), and [Synthesis results](synthesis-summary.en.md) for their respective contracts and evidence boundaries.

## Verification boundary

The reproduction documents define tested configurations, commands and success criteria. Performance results specify workloads and ideal native-interface timing. Synthesis summaries specify the target configuration, tools, corner and constraints; simulation and synthesis do not establish board or silicon qualification.

## Source and licensing

Original files carry organization, project and license notices. Strict-format data uses adjacent license companions. Third-party materials retain their applicable copyright and terms; the root LICENSE defines the project license.

## Source delivery

The version tag identifies the current source tree, including source notices and documentation. Release downloads provide hardware sources and bundled RTL for this version. Archive annotations and documentation can differ from the online tree. For RTL integration, use the product file list rather than verification environments; use the verification and synthesis documents for supported configurations and measurement conditions.
