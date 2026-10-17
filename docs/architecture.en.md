<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks NTT/INTT Hardware Accelerator
Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
File: docs/architecture.en.md

SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Architecture

[简体中文](architecture.md)

This independent operator combines forward and inverse transforms using one butterfly pipeline, two ping-pong data planes, and a shared twiddle table. Radix-2 Stockham scheduling issues one butterfly per cycle; transform size is fixed at Chisel elaboration, without a SoC dependency or FPGA primitives.

## Configuration and tasks

`AsicNttConfig(logN)` supports `logN=4..16`, with $N=2^{\mathrm{logN}}$. Each task selects NTT or INTT. The product top is `AsicGoldilocksNttPair`; bundled RTL uses `logN=16` and one butterfly lane. Changing transform size does not change the task, table, two-element data-beat, or completion field definitions.

## Scheduling and pipeline

States follow Idle → Load → Issue/Drain (logN repetitions) → Output → Complete. Each stage reads $N/2$ element pairs and their twiddles from the source plane, writes the other plane, and swaps roles when all results have landed. Stages respect data dependencies; tasks execute sequentially across completion boundaries.

The modular multiplier uses four 32×32 partial products, segmented prefix addition/subtraction, and special-modulus reduction. Its latency is 11 cycles and initiation interval is one cycle. The butterfly adds modular sum/difference and division by two in inverse mode, with a latency of 17 cycles and initiation interval of one. Synchronous memory access and operand registers add one cycle each, for a 19-cycle stage drain budget. Pipelining applies to the butterfly datapath; task throughput includes loading, all compute stages, and output scheduling.

## Storage

| Resource | Count | Depth×width per resource | Purpose |
| --- | ---: | --- | --- |
| Data plane | 2 | Two N/2×64 banks per plane | Ping-pong source/destination |
| Twiddle memory | 1 | N×64 | N/2 entries per direction |

Capacity is $3N\times64$ bits, or 1.5 MiB at N65536. The XOR of all logical address bits selects a bank; the address excluding its least significant bit selects the row. Each radix-2 pair differs in one address bit, so its elements occupy different banks. Each bank has one read and one write port with a one-cycle synchronous read contract.

Product memories have no reset clearing or file initialization. ASIC integrators map SRAM to this port and latency contract. Product SV instantiates no vendor SRAM, DSP, or clock primitives. Reference-vector ROMs belong to verification environments and are excluded from the product file list.

## Interface timing isolation

Task, table, and input use non-flow-through FIFOs with capacities of 1, 2, and 2; output uses a four-entry FIFO. Pending synchronous-read responses reserve output space, preserving data during backpressure and enabling consecutive two-element output beats under ideal conditions. See [Native interface](interface.en.md) for handshakes and reset behavior.
