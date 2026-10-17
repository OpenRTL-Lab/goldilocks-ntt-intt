<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks NTT/INTT Hardware Accelerator
Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
File: docs/interface.md

SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 接口说明

[English](interface.en.md)

产品使用五组同步原生 valid／ready 接口，分别传送任务、旋转因子、输入、输出和完成信息；每个有效数据元素为规范化的 64 位 Goldilocks 域元素，任务标签随完成信息返回，正向和逆向任务共用同一个产品实例。

这里 N = 2^logN 是生成电路时固定的变换点数（logN=4..16，随包 RTL 为 N=65,536），一个任务处理 N 个元素。规范域元素表示整数 0 ≤ x < p，其中 p = 2^64 - 2^32 + 1，不是 Montgomery 编码的残数。32 位 tag 是调用方提供并在完成时原样返回的标识，不参与变换计算。

## 信号契约

顶层 `AsicGoldilocksNttPair`，`clock` 上升沿工作，`reset` 同步高有效。握手发生于该沿 `valid && ready`；发送方在阻塞时保持 valid 和 payload 不变。SV 字段采用 `io_<通道>_bits_<字段>` 命名。

| 通道 | 相对产品方向 | 字段 | 帧约定 |
| --- | --- | --- | --- |
| task | 输入 | inverse:1、tag:32 | inverse=0 为 NTT，1 为 INTT |
| table | 输入 | inverse:1、data:64、last:1 | 每方向 N/2 拍，last 仅最后一拍 |
| input | 输入 | data_0:64、data_1:64、last:1 | N/2 拍，依次为 x[2i]、x[2i+1] |
| output | 输出 | data_0:64、data_1:64、last:1 | N/2 拍，依次为 y[2i]、y[2i+1] |
| completion | 输出 | inverse:1、tag:32、status:2、computeCycles:64 | 全部输出完成后返回 |

`io_configured[0]` 表示正向表已提交，`[1]` 表示逆向表已提交。外部最终 table 握手只是进入入口 FIFO，必须等待对应 configured 位才能确认内部表配置结束。表可在空闲时加载，任务和表同时等待时，内部表提交优先。

## 标准调用顺序

先复位，依次加载一或两个方向的表；等待 configured；发送任务；连续提供 N/2 个输入拍；接收 N/2 个输出拍；接收完成信息，再提交后续任务。正确旋转因子格式见 [数学模型](mathematical-model.md)。表在同尺寸同单位根的连续任务间复用，不必每任务重载。

完成信息的 inverse 和 tag 与请求相同。`computeCycles` 统计计算状态：Issue 发射当前变换级的蝶形，Drain 等待该级在途读请求和算术结果排空后再进入下一级；不含表配置、输入装载、输出和完成等待。

## 错误与复位

| status | 含义 | 行为 |
| ---: | --- | --- |
| 0 | 成功 | 返回完整输出和完成信息 |
| 1 | 请求方向未配置 | 不接收数据帧，不输出结果，computeCycles=0 |
| 2 | 输入帧 last 或规范化检查失败 | 接收固定 N/2 拍后返回错误，不输出结果，computeCycles=0 |

数据须在 $0\le x<p$。last 必须严格出现在帧末，早到或缺失均为错误。无效表数据或错误 last 不会产生可用配置；重新提供正确的完整表。产品检查表的范围和帧结构，不验证单位根阶数或表元素的数学正确性。

复位终止在途任务、清空协议状态并撤销 configured；存储内容无需清零，恢复后重载表及完整输入即可。输入装载和输出可背压；内部计算流水线固定推进，输出 FIFO 与在途读预留保证结果不会因消费暂停而丢失。
