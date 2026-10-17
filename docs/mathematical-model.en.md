<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks NTT/INTT Hardware Accelerator
Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
File: docs/mathematical-model.en.md

SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# Mathematical model

[简体中文](mathematical-model.md)

The circuit implements power-of-two NTT and normalized INTT over the Goldilocks prime field. Inputs and outputs use natural indexing; the forward transform uses positive root powers and the inverse uses negative powers with $1/N$ scaling. Programmable twiddles make the root choice an explicit interface contract.

## Field and transforms

$$
p=2^{64}-2^{32}+1=18446744069414584321,\qquad
\mathbb F_p=\mathbb Z/p\mathbb Z,\qquad N=2^m,\quad4\le m\le16.
$$

Elements are canonical integers $0\le x<p$. Select a root satisfying:

$$
\omega^N=1,\qquad\omega^{N/2}=-1\pmod p.
$$

Forward and inverse operations are:

$$
y_k=\sum_{j=0}^{N-1}x_j\omega^{jk}\pmod p,\qquad0\le k<N,
$$

$$
x_j=N^{-1}\sum_{k=0}^{N-1}y_k\omega^{-jk}\pmod p,\qquad0\le j<N.
$$

In beat i, data_0 and data_1 carry elements 2i and 2i+1. Callers perform no bit reversal. Each inverse butterfly scales both results by $2^{-1}$ at each of m stages, yielding $2^{-m}=N^{-1}$ normalization.

## Twiddle configuration

Each direction supplies N/2 canonical elements in increasing address order:

$$
T_{\mathrm{fwd}}[i]=\omega^i\pmod p,\qquad
T_{\mathrm{inv}}[i]=\omega^{-i}\pmod p,\qquad0\le i<N/2.
$$

Forward and inverse roots must be reciprocal for the mathematical transform pair. The circuit checks range and framing; callers ensure root order and table contents. Other canonical tables execute their programmed butterfly network, not necessarily the NTT/INTT defined above.

Reference tests use numerical values matching the [Proofman root table](https://github.com/0xPolygonHermez/pil2-proofman/blob/main/fields/src/goldilocks.rs); the N65536 root is **8167150655112846419**. Values for m=0..16 are in the [Field reference definition](../common/goldilocks/src/main/scala/ntt/GoldilocksReference.scala). Tests independently check exact root orders and adjacent-square coherence. See [Third-party notices](../THIRD_PARTY_NOTICES.en.md) for attribution.

## Exact Stockham scheduling

Let A be the source plane and B the destination. At stage $s=0,\ldots,m-1$:

$$
h=2^{m-1-s},\quad q=0,\ldots,N/2-1,\quad
g=\lfloor q/h\rfloor h,\quad r=q-g,
$$

$$
a=A[2g+r],\quad b=A[2g+r+h],\quad
t=T[g],\quad u=a+bt\pmod p,\quad v=a-bt\pmod p.
$$

Write $B[q]=u$ and $B[q+N/2]=v$; inverse mode multiplies both by $2^{-1}$ before writing. Swap A and B at every stage; the final stage produces natural-order results. T selects the requested direction, and r is the offset within the current h-element group.

## Polynomial interpretation

For $f(X)=\sum_jx_jX^j$, NTT returns $f(\omega^k)$; INTT recovers coefficients from those evaluations. Low-degree extension can use INTT, external zero padding and optional coset scaling, then an appropriately sized NTT. Each transform size requires its corresponding generated instance and root configuration; coset scaling and padding are external operations.
