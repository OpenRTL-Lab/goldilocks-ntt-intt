<!--
OpenRTL-Header-Begin
OpenRTL — Open-source hardware accelerators.
Organization: https://github.com/OpenRTL-Lab
Website: https://openrtl.top/

Project: Goldilocks NTT/INTT Hardware Accelerator
Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
File: docs/toolchain.md

SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
Copyright applies to original project contributions only.
SPDX-License-Identifier: MIT
See LICENSE and applicable third-party notices.
OpenRTL-Header-End
-->

# 工具链说明

[English](toolchain.en.md)

公开复现采用 JDK 21、Mill 1.1.7、Scala 2.13.18、Chisel 7.13.0、firtool 1.149.0、Verilator 5.050、Python 3.12 和 GCC 16.2.0；生成与仿真不依赖商业 EDA、工艺库、预先安装的项目仓库或机器专用目录。

## 安装和依赖

在 Linux 环境安装 JDK 21、Python 3.12、GNU Make 与 GCC／G++（支持 C11 `__uint128_t` 和 C++17）。按 [Mill 安装指南](https://mill-build.org/mill/1.1.x/cli/installation-ide.html) 安装版本 1.1.7，按 [Verilator 安装指南](https://verilator.org/guide/latest/install.html) 安装版本 5.050。Java、mill、python3、cc、g++、make、verilator 应在 PATH 中。

`build.mill` 固定 Scala、Chisel 和插件版本，首次执行 Mill 时会解析 Maven 依赖和匹配的 firtool。依赖下载需要联网；已经准备好这些依赖时可使用自己的缓存。脚本不会要求内部仓库或固定缓存位置。

检查基础工具是否可用及版本后，再进行生成和验证：

```bash
java -version
mill --version
python3 --version
cc --version
g++ --version
make --version
verilator --version
```

## 自定义入口

`MILL` 可指定 Mill 启动命令，`CC` 可指定 C 编译命令。标准环境无需设置它们。需要选择自己的启动器时，用环境变量传递，不修改产品设计或写入机器路径到仓库文件。

生成文件第一行标记 firtool 版本。复现版本与这里保持一致时，验证脚本同时检查重新生成产品与随包产品的电路文本一致（忽略定位注释和空白）。构建目录中的日志和中间文件留作本地诊断，不属于发行包。完整操作见 [快速复现](reproduction.md)。
