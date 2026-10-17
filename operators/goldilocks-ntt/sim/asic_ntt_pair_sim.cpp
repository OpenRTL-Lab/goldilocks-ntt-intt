/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks NTT/INTT Hardware Accelerator
 * Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
 * Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
 * File: operators/goldilocks-ntt/sim/asic_ntt_pair_sim.cpp
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

#include "VAsicNttPairSimTop.h"
#include "verilated.h"
#include <cstdint>
#include <iostream>

// Driver deliberately has no native-interface access or reference arithmetic.
int main(int argc, char **argv) {
  VerilatedContext context;
  context.commandArgs(argc, argv);
  VAsicNttPairSimTop top{&context};
  auto tick = [&]() {
    top.clock = 0; top.eval(); context.timeInc(1);
    top.clock = 1; top.eval(); context.timeInc(1);
  };
  top.reset = 1; top.trigger = 0;
  for (int i = 0; i < 8; ++i) tick();
  top.reset = 0; tick(); top.trigger = 1; tick(); top.trigger = 0;
  std::uint64_t ticks = 0;
  while (!top.done && !context.gotFinish() && ticks < 20000000ULL) {
    tick(); ++ticks;
  }
  const bool ok = top.done && top.pass && !top.timedOut &&
    top.mismatchCount == 0 && top.framesCompleted == 10;
  std::cout << "RESULT pass=" << ok << " done=" << unsigned(top.done)
    << " timedOut=" << unsigned(top.timedOut)
    << " mismatches=" << top.mismatchCount << " checkedWords=" << top.checkedWords
    << " frames=" << unsigned(top.framesCompleted) << " cycles=" << top.cycles << '\n';
  #define METRIC(I) std::cout << "FRAME index=" << I \
    << " compute=" << top.computeCycles_##I << " frame=" << top.frameCycles_##I \
    << " task=" << top.taskCycles_##I << '\n';
  METRIC(0) METRIC(1) METRIC(2) METRIC(3) METRIC(4)
  METRIC(5) METRIC(6) METRIC(7) METRIC(8) METRIC(9)
  #undef METRIC
  top.final();
  return ok ? 0 : 1;
}
