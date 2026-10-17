<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks NTT/INTT Hardware Accelerator
Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
File: docs/architecture.md

SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 架构说明

[English](architecture.en.md)

本模块将正向和逆向变换合并为一个独立算子，采用单蝶形流水线、两组乒乓存储平面和共享旋转因子表，以每周期一组蝶形运算完成 radix-2 Stockham 调度；尺寸在 Chisel 生成时确定，接口不依赖 SoC 或 FPGA 原语。

## 参数和任务

`AsicNttConfig(logN)` 支持 `logN=4..16`，$N=2^{\mathrm{logN}}$；每个任务选择 NTT 或 INTT。产品顶层为 `AsicGoldilocksNttPair`，随包配置为 `logN=16`、单蝶形通道。参数配置改变点数，不改变任务、表、双元素数据拍与完成接口的字段。

## 调度与流水线

状态序列为 Idle → Load → Issue／Drain（重复 logN 次）→ Output → Complete。每个阶段从源平面连续读取 $N/2$ 对元素和对应旋转因子，向另一个平面写入结果；全部结果落地后交换平面。阶段之间等待数据依赖，任务之间按完成边界顺序处理。

模乘采用四个 32×32 部分积、分段前缀加减和 Goldilocks 特殊模数约减，延迟 11 周期，发起间隔 1 周期。蝶形含乘法、模加减和逆变换时的模除二，延迟 17 周期，发起间隔同样为 1 周期。同步存储读取与操作数寄存各占一周期，阶段排空预算合计为 19 周期。流水化的是蝶形数据通路；整任务吞吐率由 Load、各阶段和 Output 的调度共同决定。

## 存储结构

| 资源 | 个数 | 每个深度×位宽 | 用途 |
| --- | ---: | --- | --- |
| 数据平面 | 2 | 每平面两个 N/2×64 bank | 源／目标乒乓交换 |
| 旋转因子存储 | 1 | N×64 | 正向和逆向各 N/2 项 |

总容量为 $3N\times64$ 位，N65536 时为 1.5 MiB。逻辑地址的全部位异或形成 bank 号，除最低位后的地址形成行号；radix-2 蝶形的两地址只相差一位，必然命中不同 bank。每 bank 采用一读一写、同步读取延迟一周期的抽象存储。

产品存储无复位清零、无文件初始化；ASIC 集成方按此端口和读延迟契约映射 SRAM。产品 SV 不实例化厂商 SRAM、DSP 或时钟原语。验证环境中的参考向量 ROM 是测试设施，不属于产品文件清单。

## 接口时序隔离

任务、表、输入分别有 1／2／2 项非直通 FIFO，输出有 4 项 FIFO。输出为在途同步读响应预留空间，维持背压期间的数据完整性，并支持理想条件下连续双元素输出。详细握手与复位行为见 [接口说明](interface.md)。
