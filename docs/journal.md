<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks NTT/INTT Hardware Accelerator
Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
File: docs/journal.md

SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 当前版本说明

[English](journal.en.md)

Goldilocks NTT/INTT硬件加速器，版本1.1.0。采用Chisel实现Goldilocks域上的NTT和逆NTT，并提供生成的SystemVerilog。

## 交付与使用

仓库提供 Chisel 源码、生成的产品 SystemVerilog、原生接口文档、数学参考模型和复现工具。集成电路使用 [产品文件清单](../rtl/v1.1.0/lane1-n65536/files.f)，不包含验证环境。先读 [接口说明](interface.md)，再按需要阅读 [快速复现](reproduction.md)、[性能指标](performance.md) 和 [综合结果](synthesis-summary.md) 中各自的契约与证据边界。

## 验证边界

复现文档定义已测配置、命令和成功判据；性能结果给出工作负载与理想原生接口条件。综合摘要说明目标配置、工具、工艺角与约束；仿真和综合不等于上板或硅后认证。

## 来源与许可

原创文件具有组织、项目和许可声明；严格格式数据使用相邻许可伴随文件。第三方材料保留相应版权与许可，项目许可见根目录 LICENSE。

## 源码交付

版本标签标识包含来源声明和文档的当前源码树；Release 下载提供本版本硬件源码与随包 RTL。源码包与在线目录的注释及文档可能不同。RTL 集成使用产品文件清单，不把验证环境作为产品电路；支持配置、验证范围和测量条件见验证及综合文档。
