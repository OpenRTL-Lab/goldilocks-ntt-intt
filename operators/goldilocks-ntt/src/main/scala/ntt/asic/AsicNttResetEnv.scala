/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks NTT/INTT Hardware Accelerator
 * Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
 * Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
 * File: operators/goldilocks-ntt/src/main/scala/ntt/asic/AsicNttResetEnv.scala
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

package ntt.asic

import chisel3._
import chisel3.util._

/** Native-interface-only abort probes. The ENV itself is NOT locally reset.
  * Tables of canonical ones and zero input suffice for these protocol tests;
  * the independent C-reference ENV runs only after all four aborts recover.
  */
private[asic] class AsicNttResetPreflight(cfg: AsicNttConfig) extends Module {
  val io = IO(new Bundle {
    val trigger = Input(Bool())
    val localReset = Output(Bool())
    val done = Output(Bool())
    val pass = Output(Bool())
    val mismatchCount = Output(UInt(32.W))
    val dut = Flipped(new AsicNttPairIO)
  })
  object State extends ChiselEnum {
    val Idle, LoadForward, LoadInverse, CheckConfigured, SendTask, SendInput,
        WaitAbort, ResetPulse, CheckReset, ProbeTask, ProbeWait, Guard, Done = Value
  }
  private val state = RegInit(State.Idle)
  // 0: Issue; 1: first-stage Drain; 2: blocked output; 3: blocked completion.
  private val phase = RegInit(0.U(2.W))
  private val tableBeat = RegInit(0.U(cfg.logN.W))
  private val inputBeat = RegInit(0.U(cfg.logN.W))
  private val outputs = RegInit(0.U(32.W))
  private val age = RegInit(0.U(32.W))
  private val guardAge = RegInit(0.U(8.W))
  private val resets = RegInit(0.U(3.W))
  private val probes = RegInit(0.U(3.W))
  private val mismatches = RegInit(0.U(32.W))
  private val abortTag = "hfffe0000".U(32.W) | phase
  private val probeTag = "hfffe0100".U(32.W) | phase
  private val finalTable = tableBeat === (cfg.beats - 1).U
  private val finalInput = inputBeat === (cfg.beats - 1).U
  private val finalOutput = outputs === (cfg.beats - 1).U
  private val loadingTable = state === State.LoadForward || state === State.LoadInverse
  private val afterReset = state === State.CheckReset || state === State.ProbeTask ||
    state === State.ProbeWait || state === State.Guard

  io.localReset := state === State.ResetPulse
  io.dut.table.valid := loadingTable
  io.dut.table.bits.inverse := state === State.LoadInverse
  io.dut.table.bits.data := 1.U
  io.dut.table.bits.last := finalTable
  io.dut.task.valid := state === State.SendTask || state === State.ProbeTask
  io.dut.task.bits.inverse := phase(0)
  io.dut.task.bits.tag := Mux(state === State.ProbeTask, probeTag, abortTag)
  io.dut.input.valid := state === State.SendInput
  io.dut.input.bits.data := VecInit(Seq.fill(2)(0.U(64.W)))
  io.dut.input.bits.last := finalInput
  // No completion of an abort task can fire. Phase3 consumes its entire output
  // frame but holds completion; phase2 holds output from its first valid beat.
  io.dut.output.ready := (state === State.WaitAbort && phase === 3.U) ||
    state === State.ProbeWait || state === State.Guard
  io.dut.completion.ready := state === State.ProbeWait

  private val allowedOutput = (state === State.WaitAbort || state === State.ResetPulse) && phase >= 2.U
  private val allowedCompletion = state === State.ProbeWait ||
    ((state === State.WaitAbort || state === State.ResetPulse) && phase === 3.U)
  private val errors = Seq(
    io.dut.output.valid && !allowedOutput,
    io.dut.completion.valid && !allowedCompletion,
    allowedOutput && io.dut.output.valid && io.dut.output.bits.data.asUInt =/= 0.U,
    state === State.WaitAbort && phase === 3.U && io.dut.output.fire &&
      (outputs >= cfg.beats.U || io.dut.output.bits.last =/= finalOutput),
    state === State.WaitAbort && phase === 3.U && io.dut.completion.valid && outputs =/= cfg.beats.U,
    state === State.ResetPulse && phase === 2.U && !io.dut.output.valid,
    state === State.ResetPulse && phase === 3.U && !io.dut.completion.valid,
    afterReset && io.dut.configured =/= 0.U,
    afterReset && io.dut.input.ready,
    (state === State.CheckReset || state === State.Guard) && !io.dut.task.ready,
    state === State.ProbeWait && io.dut.completion.fire && io.dut.completion.bits.status =/= 1.U,
    state === State.ProbeWait && io.dut.completion.fire && io.dut.completion.bits.tag =/= probeTag,
    state === State.ProbeWait && io.dut.completion.fire && io.dut.completion.bits.inverse =/= phase(0),
    state === State.ProbeWait && io.dut.completion.fire && io.dut.completion.bits.computeCycles =/= 0.U
  )
  private val errorCount = PopCount(VecInit(errors))
  when(errorCount =/= 0.U) { mismatches := mismatches + errorCount }
  when(state === State.WaitAbort && phase === 3.U && io.dut.output.fire) {
    outputs := outputs + 1.U
  }

  switch(state) {
    is(State.Idle) { when(io.trigger) { state := State.LoadForward } }
    is(State.LoadForward) {
      when(io.dut.table.fire) {
        tableBeat := tableBeat + 1.U
        when(finalTable) { tableBeat := 0.U; state := State.LoadInverse }
      }
    }
    is(State.LoadInverse) {
      when(io.dut.table.fire) {
        tableBeat := tableBeat + 1.U
        when(finalTable) { tableBeat := 0.U; state := State.CheckConfigured }
      }
    }
    // The final table beat first enters the ingress FIFO; wait for its commit.
    is(State.CheckConfigured) { when(io.dut.configured === 3.U) { state := State.SendTask } }
    is(State.SendTask) {
      when(io.dut.task.fire) {
        inputBeat := 0.U
        outputs := 0.U
        state := State.SendInput
      }
    }
    is(State.SendInput) {
      when(io.dut.input.fire) {
        inputBeat := inputBeat + 1.U
        when(finalInput) { age := 0.U; state := State.WaitAbort }
      }
    }
    is(State.WaitAbort) {
      age := age + 1.U
      // age=0 is the cycle immediately after final input acceptance. Entering
      // ResetPulse at age=1 applies reset at final-input-edge+3. Likewise the
      // Drain probe applies reset at final-input-edge+beats+4, inside the first
      // stage's latency-one SRAM / seventeen-cycle butterfly drain window.
      when((phase === 0.U && age === 1.U) ||
        (phase === 1.U && age === (cfg.beats + 2).U) ||
        (phase === 2.U && io.dut.output.valid && !io.dut.output.ready) ||
        (phase === 3.U && io.dut.completion.valid && !io.dut.completion.ready)) {
        state := State.ResetPulse
      }
    }
    is(State.ResetPulse) {
      resets := resets + 1.U
      state := State.CheckReset
    }
    // This is the FIRST cycle after the synchronous local reset edge. All
    // producers are quiescent and no response can fire; valid clearing is
    // checked directly, not hidden behind a ready/valid handshake.
    is(State.CheckReset) { state := State.ProbeTask }
    is(State.ProbeTask) { when(io.dut.task.fire) { state := State.ProbeWait } }
    is(State.ProbeWait) {
      when(io.dut.completion.fire) {
        probes := probes + 1.U
        guardAge := 0.U
        state := State.Guard
      }
    }
    is(State.Guard) {
      // Let the entire old arithmetic pipeline age out without producing any
      // output or completion before reloading either direction's table.
      guardAge := guardAge + 1.U
      when(guardAge === (AsicGoldilocksButterfly.Latency + 2).U) {
        when(phase === 3.U) { state := State.Done }
          .otherwise { phase := phase + 1.U; state := State.LoadForward }
      }
    }
  }
  io.done := state === State.Done
  io.pass := io.done && mismatches === 0.U && resets === 4.U && probes === 4.U
  io.mismatchCount := mismatches
}

/** Host ABI is deliberately identical to AsicNttPairSimTop. Only the product
  * DUT receives localReset; both ENV FSMs and the global watchdog survive it.
  */
class AsicNttPairResetSimTop(cfg: AsicNttConfig, vectorDirectory: String) extends RawModule {
  override def desiredName: String = "AsicNttPairSimTop"
  val clock = IO(Input(Clock()))
  val reset = IO(Input(Bool()))
  val trigger = IO(Input(Bool()))
  val done = IO(Output(Bool()))
  val pass = IO(Output(Bool()))
  val timedOut = IO(Output(Bool()))
  val mismatchCount = IO(Output(UInt(32.W)))
  val cycles = IO(Output(UInt(64.W)))
  val checkedWords = IO(Output(UInt(64.W)))
  val framesCompleted = IO(Output(UInt(32.W)))
  val computeCycles = IO(Output(Vec(10, UInt(64.W))))
  val frameCycles = IO(Output(Vec(10, UInt(64.W))))
  val taskCycles = IO(Output(Vec(10, UInt(64.W))))

  withClockAndReset(clock, reset) {
    val preflight = Module(new AsicNttResetPreflight(cfg))
    val golden = Module(new AsicNttPairEnv(cfg, vectorDirectory, variedPacing = true))
    val started = RegInit(false.B)
    val finished = RegInit(false.B)
    val timeout = RegInit(false.B)
    val tick = RegInit(0.U(64.W))
    val goldenSelected = RegInit(false.B)
    val running = started && !finished
    val goldenTrigger = running && preflight.io.done && !goldenSelected
    val dut = withClockAndReset(clock, reset || preflight.io.localReset) {
      Module(new AsicGoldilocksNttPair(cfg))
    }
    preflight.io.trigger := trigger && !started
    golden.io.trigger := goldenTrigger
    when(goldenTrigger) { goldenSelected := true.B }
    when(trigger && !started) { started := true.B; tick := 1.U }
    when(running) { tick := tick + 1.U }

    // Gate valid in both response directions and ready in both request
    // directions: an unselected ENV must NEVER see a phantom handshake.
    dut.io.task.valid := running && Mux(goldenSelected, golden.io.dut.task.valid, preflight.io.dut.task.valid)
    dut.io.task.bits := Mux(goldenSelected, golden.io.dut.task.bits, preflight.io.dut.task.bits)
    golden.io.dut.task.ready := running && goldenSelected && dut.io.task.ready
    preflight.io.dut.task.ready := running && !goldenSelected && dut.io.task.ready
    dut.io.table.valid := running && Mux(goldenSelected, golden.io.dut.table.valid, preflight.io.dut.table.valid)
    dut.io.table.bits := Mux(goldenSelected, golden.io.dut.table.bits, preflight.io.dut.table.bits)
    golden.io.dut.table.ready := running && goldenSelected && dut.io.table.ready
    preflight.io.dut.table.ready := running && !goldenSelected && dut.io.table.ready
    dut.io.input.valid := running && Mux(goldenSelected, golden.io.dut.input.valid, preflight.io.dut.input.valid)
    dut.io.input.bits := Mux(goldenSelected, golden.io.dut.input.bits, preflight.io.dut.input.bits)
    golden.io.dut.input.ready := running && goldenSelected && dut.io.input.ready
    preflight.io.dut.input.ready := running && !goldenSelected && dut.io.input.ready
    dut.io.output.ready := running && Mux(goldenSelected, golden.io.dut.output.ready, preflight.io.dut.output.ready)
    golden.io.dut.output.valid := running && goldenSelected && dut.io.output.valid
    preflight.io.dut.output.valid := running && !goldenSelected && dut.io.output.valid
    golden.io.dut.output.bits := dut.io.output.bits
    preflight.io.dut.output.bits := dut.io.output.bits
    dut.io.completion.ready := running && Mux(goldenSelected, golden.io.dut.completion.ready, preflight.io.dut.completion.ready)
    golden.io.dut.completion.valid := running && goldenSelected && dut.io.completion.valid
    preflight.io.dut.completion.valid := running && !goldenSelected && dut.io.completion.valid
    golden.io.dut.completion.bits := dut.io.completion.bits
    preflight.io.dut.completion.bits := dut.io.completion.bits
    golden.io.dut.configured := dut.io.configured
    preflight.io.dut.configured := dut.io.configured

    val timeoutNow = running && tick === 19999999.U
    when(running && goldenSelected && golden.io.done) { finished := true.B }
    when(timeoutNow) { timeout := true.B; finished := true.B }
    done := finished
    timedOut := timeout || golden.io.timedOut
    pass := finished && !timedOut && preflight.io.pass && golden.io.pass
    mismatchCount := preflight.io.mismatchCount + golden.io.mismatchCount + timeout.asUInt
    cycles := tick
    checkedWords := golden.io.checkedWords
    framesCompleted := golden.io.framesCompleted
    computeCycles := golden.io.computeCycles
    frameCycles := golden.io.frameCycles
    taskCycles := golden.io.taskCycles
  }
}
