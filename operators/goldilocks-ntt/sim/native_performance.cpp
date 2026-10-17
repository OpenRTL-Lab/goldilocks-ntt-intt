/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks NTT/INTT Hardware Accelerator
 * Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
 * Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
 * File: operators/goldilocks-ntt/sim/native_performance.cpp
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

#include "VAsicGoldilocksNttPair.h"
#include "verilated.h"
#include <algorithm>
#include <cstdint>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <string>
#include <vector>

using Words = std::vector<std::uint64_t>;
static Words read(const std::string& path, bool packed) {
  std::ifstream file(path);
  if (!file) throw std::runtime_error("Missing vector file");
  Words words; std::string line;
  while (file >> line) {
    if (line.size() != (packed ? 32U : 16U)) throw std::runtime_error("Bad vector width");
    words.push_back(std::stoull(line.substr(packed ? 16 : 0, 16), nullptr, 16));
    if (packed) words.push_back(std::stoull(line.substr(0,16), nullptr,16));
  }
  return words;
}

int main(int argc, char** argv) {
  if (argc != 5) throw std::runtime_error("usage: native_performance vectors logN tasks isolated");
  const unsigned logn = std::stoul(argv[2]), tasks = std::stoul(argv[3]);
  if (logn < 4 || logn > 16 || tasks < 8 || tasks > 128) throw std::runtime_error("Bad size");
  const std::size_t n = std::size_t{1} << logn, beats = n/2;
  const bool isolated = std::stoul(argv[4]) != 0;
  const std::string dir = argv[1];
  const auto input = read(dir+"/input.hex",true), expected = read(dir+"/expected.hex",true);
  const auto fwd = read(dir+"/forward-twiddle.hex",false), inv = read(dir+"/inverse-twiddle.hex",false);
  if (input.size()!=10*n || expected.size()!=10*n || fwd.size()!=beats || inv.size()!=beats)
    throw std::runtime_error("Vector size mismatch");
  VerilatedContext context; context.commandArgs(argc,argv);
  VAsicGoldilocksNttPair top{&context};
  std::uint64_t cycle=0;
  auto settle = [&]() { top.clock=0; top.eval(); };
  auto edge = [&]() { top.clock=1; top.eval(); context.timeInc(1); ++cycle; };
  top.reset=1; top.io_task_valid=0; top.io_table_valid=0; top.io_input_valid=0;
  top.io_output_ready=1; top.io_completion_ready=1;
  for(int i=0;i<8;++i) {settle();edge();}
  top.reset=0;
  unsigned direction=0; std::size_t table=0;
  while(direction<2) {
    top.io_table_valid=1; top.io_table_bits_inverse=direction;
    top.io_table_bits_data=direction ? inv[table] : fwd[table];
    top.io_table_bits_last=(table+1==beats);
    settle(); const bool fire=top.io_table_ready; edge();
    if(fire && ++table==beats) {table=0;++direction;}
  }
  top.io_table_valid=0;
  for(unsigned wait=0;;++wait) {
    settle(); if(top.io_configured==3) break; edge();
    if(wait>20) throw std::runtime_error("Table did not commit");
  }
  unsigned accepted=0,completed=0,active=0;
  std::size_t inbeat=0,outbeat=0;
  std::uint64_t start=0,firstInput=0,lastOutput=0,previousComplete=0,errors=0;
  const auto limit=cycle+tasks*(n*(logn+4)+1000ULL)+1000;
  while(completed<tasks && cycle<limit && !context.gotFinish()) {
    top.io_task_valid=accepted<tasks && (!isolated || (accepted==completed &&
      (accepted==0 || cycle>previousComplete+1)));
    top.io_task_bits_inverse=(accepted%10)&1; top.io_task_bits_tag=accepted;
    top.io_input_valid=accepted>completed && inbeat<beats;
    if(top.io_input_valid) {
      const auto offset=(active%10)*n+2*inbeat;
      top.io_input_bits_data_0=input[offset]; top.io_input_bits_data_1=input[offset+1];
      top.io_input_bits_last=(inbeat+1==beats);
    }
    settle();
    const bool taskFire=top.io_task_valid && top.io_task_ready;
    const bool inputFire=top.io_input_valid && top.io_input_ready;
    const bool outputFire=top.io_output_valid;
    const bool completeFire=top.io_completion_valid;
    if(taskFire) {
      if(accepted!=completed) throw std::runtime_error("Unexpected overlapping task");
      active=accepted++; start=cycle; inbeat=outbeat=0;
      std::cout<<"ACCEPT tag="<<active<<" cycle="<<cycle<<'\n';
    }
    if(inputFire) {if(inbeat==0)firstInput=cycle;++inbeat;}
    if(outputFire) {
      if(outbeat>=beats || accepted==completed) throw std::runtime_error("Unexpected output");
      const auto offset=(active%10)*n+2*outbeat;
      errors+=(top.io_output_bits_data_0!=expected[offset]);
      errors+=(top.io_output_bits_data_1!=expected[offset+1]);
      errors+=(bool(top.io_output_bits_last)!=(outbeat+1==beats));
      if(++outbeat==beats)lastOutput=cycle;
    }
    if(completeFire) {
      if(inbeat!=beats || outbeat!=beats) throw std::runtime_error("Incomplete frame");
      errors+=(top.io_completion_bits_status!=0 || top.io_completion_bits_tag!=active ||
               top.io_completion_bits_inverse!=((active%10)&1));
      std::cout<<"COMPLETE tag="<<active<<" cycle="<<cycle<<" latency="<<cycle-start
        <<" frame="<<lastOutput-firstInput<<" compute="<<top.io_completion_bits_computeCycles
        <<" errors="<<errors<<'\n';
      ++completed; previousComplete=cycle;
    }
    edge();
  }
  const bool pass=completed==tasks && errors==0;
  std::cout<<"RESULT pass="<<pass<<" tasks="<<completed<<" checked="<<completed*n
    <<" mismatches="<<errors<<'\n';
  top.final(); return pass?0:1;
}
