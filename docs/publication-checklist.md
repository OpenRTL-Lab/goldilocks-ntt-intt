<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks NTT/INTT Hardware Accelerator
Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
File: docs/publication-checklist.md

SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 交付内容检查

[English](publication-checklist.en.md)

本仓库按独立硬件 IP 组织交付：产品 SV 与 Chisel 可重生成且已比较一致，功能与理想性能链路可独立运行，文档提供完整中英配对，综合只公开条件和结果转述，发行包包含仓库公开内容而不含机器与内部运行信息。

## 内容边界

- 产品、参考模型、生成入口、仿真环境和原生性能驱动均随包提供。
- 典型配置固定为单蝶形 N65536，其他点数可从 Chisel 参数重新生成。
- 仿真环境从产品文件清单中分离；通用存储不包含 FPGA 或专有 SRAM 原语。
- README、架构、接口、模型、复现、性能、工具链、综合摘要和第三方声明都有对应语言版本。
- 英文 README 索引英文文档，同一目标的链接文字在同一语言中一致。

## 独立复验

全部 logN=4..16 的功能、孤立和持续任务测试通过；协议、复位及人工错一位负控符合预期。随包 N65536 产品电路与重新生成版本去掉源码定位和空白后完全一致，分别运行所得结果相同。操作与通过条件见 [快速复现](reproduction.md)。

## 公开载荷审查

公开检查器使用文件白名单，检查绝对机器路径、凭证样式、私钥、内部运行标识、追溯摘要和源位置注释，并验证双语配对与链接目标。运行日志、对象、缓存与版本控制管理信息不进入公开载荷。源码中模数、单位根、测试种子和域元素是必要的数学参数或测试数据，不是身份凭证。

## 发行形式

版本见 [版本号](../VERSION)，提交信息包含版本。GitHub Release 附件为完整公开仓库源码包，包含设计、SV、仿真和文档，排除 Git 数据库及构建输出。综合仅用 [综合结果](synthesis-summary.md) 转述，不提供内部归档。
