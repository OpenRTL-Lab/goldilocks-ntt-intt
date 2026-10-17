<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks NTT/INTT Hardware Accelerator
Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
File: README.zh-CN.md

SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# goldilocks-ntt-intt 硬件加速器

[English](README.md)

本仓库提供 Goldilocks 域上独立的数论变换（NTT）与逆数论变换（INTT）硬件加速器，采用 Chisel 编写并提供生成的 SystemVerilog。模块用于 STARK 证明及低度扩展链路中的多项式系数／求值转换，通过原生接口集成到 ASIC 或 FPGA。

[发布 v1.1.0](https://github.com/OpenRTL-Lab/goldilocks-ntt-intt/releases/tag/v1.1.0) · [接口说明](docs/interface.md) · [快速复现](docs/reproduction.md) · [性能指标](docs/performance.md) · [综合结果](docs/synthesis-summary.md)

采用 [MIT 许可证](LICENSE)，允许免费商用、修改和再分发，须保留版权及许可声明；参数来源见 [第三方声明](THIRD_PARTY_NOTICES.md)。

## 计算与任务单位

一个任务是一遍完整的 N 点正变换或归一化逆变换。域元素采用无符号 64 位规范表示，即模数 p = 2^64 - 2^32 + 1 下的 0 ≤ x < p；一个数据拍携带同一变换的两个连续元素，不是两个任务。单蝶形流水线在各级变换间复用，不表示每拍完成一遍变换。旋转因子表须在任务受理前配置，下面的任务计时不包含装表。

## 配置速览

- **数域：** $p=2^{64}-2^{32}+1$，64位规范域元素。
- **变换：** radix-2 NTT和归一化INTT，自然顺序输入输出。生成器支持16～65,536点（`logN=4..16`）；随包SV为单蝶形、65,536点配置。
- **集成：** 原生valid/ready任务、旋转因子表、双元素数据和完成通道；通用同步一读一写存储接口由集成方映射到目标SRAM。
- **交付：** Chisel、生成的产品SV、独立C/Python参考模型、仿真和性能工具及双语文档。

## 仓库内容

- [Chisel 设计](operators/goldilocks-ntt/src/main/scala/ntt/asic/)：产品算术、变换调度与独立验证环境。
- [生成好的 SV](rtl/v1.1.0/lane1-n65536/)：单蝶形流水线、65,536 点配置的完整产品电路。
- [生成脚本](tools/emit_sv.py) 与 [验证脚本](tools/verify.py)：独立生成、C/Python 参考校验和 RTL 仿真。
- [C 参考模型](operators/goldilocks-ntt/cmodel/asic_ntt_reference.c) 与 [原生接口性能驱动](operators/goldilocks-ntt/sim/native_performance.cpp)：逐字结果比较和持续任务测量。
- [架构说明](docs/architecture.md)、[接口说明](docs/interface.md) 与 [工具链说明](docs/toolchain.md)：配置、流水线、存储和集成约定。

## 应用场景

当系统需要在 Goldilocks 域上完成多项式插值或批量求值时，可将 NTT/INTT 工作交给本模块。例如在 STARK 证明链路中完成系数恢复与求值域转换，或在低度扩展链路中承担 INTT、NTT 两段变换。低度扩展的补零和陪集缩放可由外部数据通路连接；这些是集成示例，不限定模块的应用范围。

## 性能指标

65,536 点配置在理想接口下，RTL实测单任务延迟为 590,133 周期、持续任务间隔为 590,134 周期；假定 500 MHz 换算约为 1.180266 ms 和 847.27 任务／秒，不是板级或硅后实测。计算阶段 99.9421% 有效忙率是 524,288 次有效蝶形发射除以 524,592 个计算阶段周期，不是全任务或外部接口忙率；测法见 [性能指标](docs/performance.md)。

## 数学模型

模块在模数 $2^{64}-2^{32}+1$ 的有限域上执行自然顺序输入、自然顺序输出的 radix-2 NTT/INTT；INTT 已包含 $1/N$ 归一化，单位根和旋转因子表由原生接口配置，严格定义见 [数学模型](docs/mathematical-model.md)。

## 快速复现

从 [发布 v1.1.0](https://github.com/OpenRTL-Lab/goldilocks-ntt-intt/releases/tag/v1.1.0) 下载完整源码包，或浏览上面的源码与SV目录。版本标签标识包含来源声明和文档的当前源码树；Release 下载提供本版本硬件源码与随包 RTL。源码包与在线目录的注释及文档可能不同。RTL 集成使用产品文件清单，不把验证环境作为产品电路；支持配置、验证范围和测量条件见验证及综合文档。

先按 [工具链说明](docs/toolchain.md) 安装工具，再在仓库根目录生成N65536产品，输出目录必须为全新目录：

```bash
python3 tools/emit_sv.py --out build/emit --logn 16
```

该命令仅生成、不运行验证。从 Chisel 重新生成电路，以独立 C/Python 模型逐字检查输出，并运行随包 SV、协议、复位和性能测试；完整命令及结果说明见 [快速复现](docs/reproduction.md)。连接主机或存储系统前先读 [接口说明](docs/interface.md)。

## 验证范围与限制

已记录的功能回归覆盖13种支持尺寸、130个正逆任务及1,310,560个结果元素，零差异；另有背压、错误输入、四个执行边界的复位恢复、人工翻转参考结果一位的负控，以及随包与重新生成产品SV比对。具体通过条件见 [快速复现](docs/reproduction.md)。

本模块是独立计算块，不是完整STARK证明器或完整Coset-LDE引擎，补零和陪集缩放由外部数据通路完成。未交付SoC、总线适配器或上板工程；下述DC setup结果不表示布局布线签核或板级验证。

## 综合结果

本配置使用 Design Compiler NXT 在 TSMC 28 nm HPC+、SSG 0.81 V／−40 °C、500 MHz、10% setup 时钟不确定度条件下通过 setup，99.52% 的已报告端点最差 setup 路径逻辑级数小于 50，按数据路径中名为 Z/ZN 的单元输出引脚逐个计级、不计连线或时钟树点；详细条件、面积和路径统计见 [综合结果](docs/synthesis-summary.md)。

## 公开边界

公开内容覆盖 Chisel、生成好的产品 SV、可复现的生成与仿真链路、双语文档及综合结果转述。抱歉，受工艺库许可和保密要求限制，我们无法提供所使用的工艺库、SRAM 库、原始 DC Tcl／约束／报告或综合快照。交付范围是独立算子，不包含 SoC、总线适配器或上板工程。

## OpenRTL与定制集成

通过 [OpenRTL组织主页](https://github.com/OpenRTL-Lab) 和 [ZK加速器集合](https://github.com/OpenRTL-Lab/zk-accelerators) 查找相关域运算、LDE、FRI与哈希模块。需要定制数据通路、接口或目标平台集成时，请使用组织主页的合作联系入口。MIT代码的使用不要求购买定制服务；不要在公开Issue中提交保密规格。
