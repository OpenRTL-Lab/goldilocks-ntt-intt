<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks NTT/INTT Hardware Accelerator
Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
File: docs/reproduction.md

SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 快速复现

[English](reproduction.en.md)

本流程从公开 Chisel 源码生成产品与验证环境，以 C 模型和独立 Python 大整数模型校验参考向量，逐字检查 RTL 结果，并复验随包 SV、协议恢复和理想接口延迟／吞吐率；所有测试输出写入自选的全新 build 子目录。

## 准备工具和目录

先按 [工具链说明](toolchain.md) 安装生成和仿真所需工具，并完成该文档的版本检查。解压完整源码包或克隆仓库后，打开包含 `build.mill` 的仓库根目录。后续所有命令都在这个目录运行；无需其他工程、商业 EDA 或工艺库。

固定发布源码与附件位于 [发布 v1.1.0](https://github.com/OpenRTL-Lab/goldilocks-ntt-intt/releases/tag/v1.1.0)。若使用Git，以下命令克隆该发布标签，获取文档对应的配置：

```bash
git clone --branch v1.1.0 --depth 1 https://github.com/OpenRTL-Lab/goldilocks-ntt-intt.git
cd goldilocks-ntt-intt
```

版本标签标识包含来源声明和文档的当前源码树；Release 下载提供本版本硬件源码与随包 RTL。源码包与在线目录的注释及文档可能不同。RTL 集成使用产品文件清单，不把验证环境作为产品电路；支持配置、验证范围和测量条件见验证及综合文档。

`--logn m` 选择 N = 2^m 点。生成的功能测试每尺寸使用十个参考帧；`--tasks` 设置单独的原生性能测试任务数（默认每模式 32 个），不是功能参考帧数。

## 运行完整验证

以下命令生成五个典型尺寸（16、64、1024、16384、65536 点），运行正逆逐字功能验证、各尺寸原生性能测试及小尺寸协议／复位／负控，并复验随包 N65536 SV：

```bash
python3 tools/verify.py --out build/verify --jobs 8
```

`--jobs` 为 Verilator 编译并行度，可按机器资源降低至 2。`--out` 必须是 build 下未存在的相对目录，脚本不删除旧输出。通过时 `results.json` 的 status=0；每个正向测试的 pass=1、mismatches=0。negative 的 pass=0、mismatches=1 表示负控成功检测到人工错误，是整体通过条件的一部分。

为了复现 [性能指标](performance.md) 中完整 13 种尺寸的数据，使用以下命令覆盖所有支持尺寸：

```bash
python3 tools/verify.py --out build/verify-all --logn 4 5 6 7 8 9 10 11 12 13 14 15 16 --tasks 32 --jobs 8
```

## 仅生成产品电路

若只需要产品 SV，以下命令生成 65,536 点配置，不创建验证 ROM 或参考文件初始化：

```bash
python3 tools/emit_sv.py --out build/emit --logn 16
```

改变 `--logn` 为 4..16 可生成其他点数。产品入口文件是输出目录中的 `rtl/product.f`，顶层为 `AsicGoldilocksNttPair`。编译文件清单使用仓库相对路径，应从根目录调用。

检查随包产品 SV 的语法、位宽与断言时，运行：

```bash
verilator --lint-only --assert --top-module AsicGoldilocksNttPair -f rtl/v1.1.0/lane1-n65536/files.f
```

## 测试链路和输出解释

C 模型独立实现模运算、DIT NTT／INTT、边界审计、根阶数审计、往返检查，并对 N≤64 使用直接 DFT 交叉校验。Python 使用任意精度整数逐字验证 C 输入、输出和表，不调用 DUT。生成的 Chisel ENV 驱动原生接口并校验输出，C++ 时钟驱动只负责启动与结果汇总。

单独的原生性能驱动在主机预载向量后直接驱动产品接口，持续和孤立两模式均检查全部结果。结果中 performance 给出周期定义下的延迟、稳态区间和任务间隔。N65536 同时编译随包与重新生成 SV，比较模块集合、去注释／空白后的电路文本及两套运行结果。

每尺寸的 c-compile／c-reference／python-reference／emit／lint、generated、native 等日志和对象目录位于本次输出下。protocol 覆盖背压、未配置、错误帧和非法表；reset 覆盖计算发起、阶段排空、阻塞输出和阻塞完成四处中断后恢复。失败时查看对应日志，修正工具环境后选择一个新的输出目录再次执行，保留原诊断。

最后用公开载荷检查器检查文档链接、双语配对和文件内容边界：

```bash
python3 tools/public_audit.py
```

运行生成的 build／out 等缓存不属于公开文件，检查器将其排除。综合条件与转述结果见 [综合结果](synthesis-summary.md)。
