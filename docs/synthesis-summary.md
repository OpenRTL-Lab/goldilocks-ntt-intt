<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks NTT/INTT Hardware Accelerator
Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
File: docs/synthesis-summary.md

SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 综合结果

[English](synthesis-summary.en.md)

单蝶形 N65536 配置在 Design Compiler NXT、TSMC 28 nm HPC+ SSG 0.81 V／−40 °C、500 MHz 和 10% setup 时钟不确定度条件下通过 setup：最差 setup 裕量 WNS 为 +0.000022173 ns、负 setup 裕量总和 TNS 为 0、违例端点为 0；21,281 条已报告端点最差路径中，99.5207% 的 Z／ZN 逻辑级数严格小于 50。

## 综合对象与条件

| 项目 | 条件 |
| --- | --- |
| 产品 | Goldilocks NTT/INTT，v1.1.0，lane1，N65536 |
| 顶层 | AsicGoldilocksNttPair，完整产品，不含验证环境 |
| 软件 | Synopsys Design Compiler NXT X-2025.06-SP3 |
| 标准单元工艺 | TSMC 28 nm HPC+，7-track 标准单元 |
| 工艺角 | SSG，0.81 V，−40 °C |
| 时钟 | 2.000 ns 周期，500 MHz |
| Setup 时钟不确定度 | 0.200 ns，占周期 10% |
| IO 最大延迟 | 输入／输出各 1.000 ns |
| 输出负载 | 0.020 pF |
| 最大转换时间／扇出 | 0.250 ns／32 |
| 存储 | 同步1R1W（一读一写）SRAM映射，192个物理宏 |
| 面积口径 | 综合 cell 面积，含已映射 SRAM 宏 |

SRAM 与标准单元均采用该配置的最差工艺角库；时钟不确定度是 setup 预算，不是额外改变产品功能。公开 SV 使用通用同步存储行为，复现仿真不需要这些库；进行 ASIC 综合时，集成方按 [架构说明](architecture.md) 的存储端口和一周期读取契约接入自己的宏。

## Setup 与逻辑级数

| 指标 | 结果 |
| --- | ---: |
| Setup | 通过 |
| WNS | +0.000022173 ns |
| TNS（负 slack 求和） | 0 ns |
| 违例端点 | 0 |
| 已约束且报告的 setup 端点 | 21,281 |
| 逻辑级数严格 <50 | 21,179 条，99.5207% |
| 恰为 50 | 10 条 |
| 严格 >50 | 92 条，0.4323% |
| 最大级数 | 67，两条路径 |

统计对每个已约束 setup 端点取一条最差 max 数据路径；内部到内部 20,442 条，内部到 IO 235 条，IO 到内部 604 条。逻辑级数仅计算数据路径叶引脚名称严格等于 Z 或 ZN 的点，每个计一级；不按文本行数、net 行、clock-to-Q 或时钟树计数。报告路径与 live timing-points 的精确 Z／ZN 序列已交叉核对；SRAM 端点按完整引脚区分。

## 面积与资源

| 互不重叠分类 | 面积（µm²） | 占总 cell 面积 |
| --- | ---: | ---: |
| 组合逻辑（不含缓冲／反相） | 13,414.926 | 0.1892% |
| 缓冲／反相 | 905.716 | 0.0128% |
| 寄存器等非组合逻辑 | 7,561.288 | 0.1067% |
| SRAM 宏 | 7,067,731.500 | 99.6914% |
| 合计 | **7,089,613.430** | **100%** |

总 cell 面积换算为 **7.089613 mm²**，不含布线或布局留白，不是 die 面积。192 个物理 SRAM 宏实现五个逻辑存储实例，容量合计 1.5 MiB。蝶形子层次面积 12,341.140 µm²，包含其中模乘 8,916.432 µm²；这两项为包含关系，不与分类表重复相加。

该摘要转述已归档综合数据，不提供原始商业工具日志。抱歉，受工艺库许可和保密要求限制，我们无法公开工艺库、SRAM 模型、原始 DC Tcl／约束／报告及综合快照。开源可复现部分是电路生成与功能／性能仿真链路；周期实测和 500 MHz 换算见 [性能指标](performance.md)。
