<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks NTT/INTT Hardware Accelerator
Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
File: docs/interface.en.md

SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Native interface

[简体中文](interface.md)

The product uses five synchronous native valid/ready channels for tasks, twiddles, inputs, outputs, and completions. Valid data elements are canonical 64-bit Goldilocks field elements; completion returns the task tag, and one product instance supports both directions.

Here N = 2^logN is the transform length fixed when the circuit is generated (`logN=4..16`; bundled RTL: N=65,536). One task transforms N elements. A canonical word represents an integer 0 <= x < p, where p = 2^64 - 2^32 + 1; it is not a Montgomery-encoded residue. The 32-bit tag is caller-supplied metadata returned unchanged with completion, not part of the transform.

## Signal contract

Top module: `AsicGoldilocksNttPair`. Operations occur on the rising edge of `clock`; `reset` is synchronous and active high. A transfer occurs when `valid && ready` at that edge. Senders hold valid and payload stable while stalled. SV payload names follow `io_<channel>_bits_<field>`.

| Channel | Direction relative to product | Fields | Frame contract |
| --- | --- | --- | --- |
| task | Input | inverse:1, tag:32 | inverse=0: NTT; inverse=1: INTT |
| table | Input | inverse:1, data:64, last:1 | N/2 beats per direction; last on final beat only |
| input | Input | data_0:64, data_1:64, last:1 | N/2 beats containing x[2i], x[2i+1] |
| output | Output | data_0:64, data_1:64, last:1 | N/2 beats containing y[2i], y[2i+1] |
| completion | Output | inverse:1, tag:32, status:2, computeCycles:64 | Returned after all output transfers |

`io_configured[0]` indicates committed forward twiddles and bit 1 indicates committed inverse twiddles. The final external table handshake enters an ingress FIFO; wait for the corresponding configured bit to confirm internal commitment. Tables load while idle, with internal table commitment taking priority over pending tasks.

## Normal sequence

Reset, load one or both tables, wait for configured, send a task, supply N/2 input beats, receive N/2 output beats, and consume completion before submitting another task. See [Mathematical model](mathematical-model.en.md) for table contents. Consecutive tasks with the same size and roots reuse tables without reloading.

Completion inverse and tag match the request. `computeCycles` counts the compute states: Issue launches butterflies for one transform stage; Drain waits for its pending reads and arithmetic results before starting the next stage. The count excludes table programming, input loading, output, and completion waiting.

## Errors and reset

| status | Meaning | Behavior |
| ---: | --- | --- |
| 0 | Success | Full output and completion |
| 1 | Requested direction unconfigured | No input frame accepted or output produced; computeCycles=0 |
| 2 | Input last/canonicality error | Accept fixed N/2 beats, return error without output; computeCycles=0 |

Elements must satisfy $0\le x<p$. Only the final beat asserts last. Invalid table elements or last framing do not establish a usable configuration; supply a correct complete table again. The product checks table range and framing, not primitive-root order or mathematical table correctness.

Reset aborts work, clears protocol state, and invalidates configured. Memory clearing is unnecessary; reload tables and a complete input frame after reset. Input and output support backpressure. Internal arithmetic advances at fixed latency, with FIFO capacity reserved for pending reads to preserve output during consumer stalls.
