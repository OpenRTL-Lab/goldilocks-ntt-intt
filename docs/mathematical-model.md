<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks NTT/INTT Hardware Accelerator
Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
File: docs/mathematical-model.md

SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 数学模型

[English](mathematical-model.en.md)

本电路实现 Goldilocks 素域上长度为二的幂的 NTT 和归一化 INTT：输入与输出均按自然索引排列，正向使用正幂单位根，逆向使用负幂并乘 $1/N$；可编程旋转因子表使单位根选择成为显式接口契约。

## 有限域与变换

$$
p=2^{64}-2^{32}+1=18446744069414584321,\qquad
\mathbb F_p=\mathbb Z/p\mathbb Z,\qquad N=2^m,\quad 4\le m\le16.
$$

每个元素用规范化整数 $0\le x<p$ 表示。选择满足以下条件的单位根：

$$
\omega^N=1,\qquad \omega^{N/2}=-1\pmod p.
$$

正向和逆向接口分别实现：

$$
y_k=\sum_{j=0}^{N-1}x_j\omega^{jk}\pmod p,\qquad 0\le k<N,
$$

$$
x_j=N^{-1}\sum_{k=0}^{N-1}y_k\omega^{-jk}\pmod p,\qquad 0\le j<N.
$$

两元素拍的 data_0／data_1 分别为索引 $2i$／$2i+1$。不要求调用方进行位反转。逆向蝶形每级将两个结果分别乘 $2^{-1}$，总共 $m$ 级，实现 $2^{-m}=N^{-1}$ 归一化。

## 表配置

每方向传入 N/2 个规范化元素，按递增地址排列：

$$
T_{\mathrm{fwd}}[i]=\omega^i\pmod p,\qquad
T_{\mathrm{inv}}[i]=\omega^{-i}\pmod p,\qquad0\le i<N/2.
$$

正向和逆向必须使用互逆的根才能构成数学上的双向变换。电路只检查元素范围和帧结构，调用方负责表内容及根阶数。规范化但不满足上述表定义的数据会执行相应可编程蝶形网络，不能据此称为上述 NTT/INTT。

参考测试采用与 [Proofman 单位根表](https://github.com/0xPolygonHermez/pil2-proofman/blob/main/fields/src/goldilocks.rs) 一致的数值；N65536 的根为 **8167150655112846419**。完整 m=0..16 数值见 [域参考定义](../common/goldilocks/src/main/scala/ntt/GoldilocksReference.scala)，测试独立检查根的精确阶数和相邻平方关系。参数来源见 [第三方声明](../THIRD_PARTY_NOTICES.md)。

## Stockham 精确调度

源平面在每阶段记为 $A$，目标平面为 $B$。第 $s=0,\ldots,m-1$ 级，定义：

$$
h=2^{m-1-s},\quad q=0,\ldots,N/2-1,\quad
g=\lfloor q/h\rfloor h,\quad r=q-g,
$$

$$
a=A[2g+r],\quad b=A[2g+r+h],\quad
t=T[g],\quad u=a+bt\pmod p,\quad v=a-bt\pmod p.
$$

写入 $B[q]=u$、$B[q+N/2]=v$；逆向将这两个值分别乘 $2^{-1}$ 再写入。每级交换 A 和 B，最后一层即为自然顺序结果。这里 $T$ 按任务方向选择正向或逆向表，$r$ 就是 q 在当前 h 元素组中的偏移。

## 与多项式运算的对应

当 $f(X)=\sum_jx_jX^j$ 时，NTT 输出 $f(\omega^k)$；INTT 从这些求值恢复系数。低度扩展链路可先 INTT 恢复系数，再由外部补零至更大尺寸并按需做陪集缩放，再使用对应尺寸的 NTT。不同尺寸须生成对应产品实例并配置对应单位根，陪集乘法与补零不是本算子的附加操作。
