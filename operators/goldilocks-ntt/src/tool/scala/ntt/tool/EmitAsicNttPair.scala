/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks NTT/INTT Hardware Accelerator
 * Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
 * Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
 * File: operators/goldilocks-ntt/src/tool/scala/ntt/tool/EmitAsicNttPair.scala
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

package ntt.tool

import circt.stage.ChiselStage
import java.nio.file.{Files, Paths}
import ntt.asic.{AsicNttConfig, AsicGoldilocksNttPair, AsicNttPairSimTop, AsicNttPairResetSimTop}

/** Portable product and verification elaboration; each output is fresh. */
object EmitAsicNttPair extends App {
  require(args.length == 4, "usage: EmitAsicNttPair build/ID/rtl logN vectors product|ideal|protocol|reset")
  val target = Paths.get(args(0)).normalize()
  require(!target.isAbsolute && target.startsWith(Paths.get("build")) &&
    target.getFileName.toString == "rtl" && !Files.exists(target))
  val cfg = AsicNttConfig(args(1).toInt)
  val mode = args(3)
  require(Set("product", "ideal", "protocol", "reset").contains(mode))
  require(mode == "product" || Files.isDirectory(Paths.get(args(2))))
  Files.createDirectories(target)
  ChiselStage.emitSystemVerilog(
    if (mode == "product") new AsicGoldilocksNttPair(cfg)
    else if (mode == "reset") new AsicNttPairResetSimTop(cfg, args(2))
    else new AsicNttPairSimTop(cfg, args(2), mode == "protocol"),
    firtoolOpts = Array("-O=release", "-disable-all-randomization",
      "--lowering-options=disallowLocalVariables", "-split-verilog", s"-o=$target"))
}
