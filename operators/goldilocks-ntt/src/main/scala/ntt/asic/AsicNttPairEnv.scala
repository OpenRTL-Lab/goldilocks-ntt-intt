/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks NTT/INTT Hardware Accelerator
 * Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
 * Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
 * File: operators/goldilocks-ntt/src/main/scala/ntt/asic/AsicNttPairEnv.scala
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
import chisel3.util.experimental.loadMemoryFromFileInline

/** ENV-only, latency-one reference SRAM with two response slots. A pending
  * SRAM response owns a slot even before it reaches the queue. This permits
  * one read and one dequeue every cycle, including after arbitrary stalls.
  */
private[asic] class AsicNttEnvRomBeat(width: Int) extends Bundle {
  val data = UInt(width.W)
  val last = Bool()
}

private[asic] class AsicNttEnvRom(width: Int, depth: Int, file: String) extends Module {
  private val addressWidth = log2Ceil(depth)
  private val countWidth = log2Ceil(depth + 1)
  val io = IO(new Bundle {
    val start = Input(Bool())
    val base = Input(UInt(addressWidth.W))
    val length = Input(UInt(countWidth.W))
    val buffered = Output(UInt(2.W))
    val out = Decoupled(new AsicNttEnvRomBeat(width))
  })
  private val memory = SyncReadMem(depth, UInt(width.W))
  loadMemoryFromFileInline(memory, file)
  private val queue = Module(new Queue(new AsicNttEnvRomBeat(width), 2, pipe = true))
  private val address = RegInit(0.U(addressWidth.W))
  private val remaining = RegInit(0.U(countWidth.W))
  private val pending = RegInit(false.B)
  private val reserved = queue.io.count +& pending.asUInt
  private val issue = !io.start && remaining =/= 0.U &&
    (reserved < 2.U || io.out.fire)
  private val response = memory.read(address, issue)
  private val responseLast = RegEnable(remaining === 1.U, issue)
  pending := issue
  queue.io.enq.valid := pending
  queue.io.enq.bits.data := response
  queue.io.enq.bits.last := responseLast
  io.out <> queue.io.deq
  io.buffered := queue.io.count
  when(pending) { assert(queue.io.enq.ready, "ENV SRAM response lost its reserved slot") }
  assert(reserved <= 2.U, "ENV reference FIFO over-reserved")
  when(io.start) {
    assert(queue.io.count === 0.U && !pending && remaining === 0.U,
      "ENV reference stream restarted before draining")
    assert(io.length =/= 0.U && (io.base +& io.length) <= depth.U,
      "ENV reference stream outside ROM")
    address := io.base
    remaining := io.length
  }.elsewhen(issue) {
    address := address + 1.U
    remaining := remaining - 1.U
  }
}

class AsicNttPairEnvIO extends Bundle {
  val trigger = Input(Bool())
  val done = Output(Bool())
  val pass = Output(Bool())
  val timedOut = Output(Bool())
  val mismatchCount = Output(UInt(32.W))
  val cycles = Output(UInt(64.W))
  val checkedWords = Output(UInt(64.W))
  val framesCompleted = Output(UInt(32.W))
  val computeCycles = Output(Vec(10, UInt(64.W)))
  val frameCycles = Output(Vec(10, UInt(64.W)))
  val taskCycles = Output(Vec(10, UInt(64.W)))
  val dut = Flipped(new AsicNttPairIO)
}

/** All stimulus, protocol timing, bit-exact checking and metrics are RTL.
  * input.hex/expected.hex contain ten contiguous N/2-beat frames: forward,
  * inverse for each of five patterns. HIGH64 is word1; LOW64 is word0.
  * Twiddle files contain N/2 single-64-bit lines per direction.
  *
  * variedPacing adds completion/output stalls, unconfigured-direction probes,
  * early-last/noncanonical table probes, and three malformed-input probes.
  * These probes never increment golden-frame word/metric/coverage counters.
  */
class AsicNttPairEnv(cfg: AsicNttConfig, vectorDirectory: String,
    variedPacing: Boolean = false) extends Module {
  val io = IO(new AsicNttPairEnvIO)
  private val Frames = 10
  private val TimeoutCycles = 20000000L
  private val modulus = "hffffffff00000001".U(64.W)
  private val inputRom = Module(new AsicNttEnvRom(128, Frames * cfg.beats,
    s"$vectorDirectory/input.hex"))
  private val expectedRom = Module(new AsicNttEnvRom(128, Frames * cfg.beats,
    s"$vectorDirectory/expected.hex"))
  private val forwardRom = Module(new AsicNttEnvRom(64, cfg.beats,
    s"$vectorDirectory/forward-twiddle.hex"))
  private val inverseRom = Module(new AsicNttEnvRom(64, cfg.beats,
    s"$vectorDirectory/inverse-twiddle.hex"))

  object State extends ChiselEnum {
    val Idle, PreTask, PreWait, BadEarlyTable, BadCanonicalTable, BadTableCheck,
        StartTable, TablePrefetch, LoadTable, CheckTable,
        StartFrame, FramePrefetch, SendTask, SendInput, WaitCompletion,
        NegativeTask, NegativeInput, NegativeWait, Finish, Done = Value
  }
  private val state = RegInit(State.Idle)
  private val active = state =/= State.Idle && state =/= State.Done
  private val tick = RegInit(0.U(64.W))
  private val mismatches = RegInit(0.U(32.W))
  private val timeout = RegInit(false.B)
  private val checked = RegInit(0.U(64.W))
  private val completed = RegInit(0.U(32.W))
  private val compute = RegInit(VecInit(Seq.fill(Frames)(0.U(64.W))))
  private val frameTime = RegInit(VecInit(Seq.fill(Frames)(0.U(64.W))))
  private val taskTime = RegInit(VecInit(Seq.fill(Frames)(0.U(64.W))))
  private val frame = RegInit(0.U(4.W))
  private val inverseTable = RegInit(false.B)
  private val probe = RegInit(0.U(2.W))
  private val negative = RegInit(0.U(2.W))
  private val badBeat = RegInit(0.U(cfg.logN.W))
  private val inputs = RegInit(0.U(32.W))
  private val outputs = RegInit(0.U(32.W))
  private val firstInputSeen = RegInit(false.B)
  private val firstInputTick = RegInit(0.U(64.W))
  private val taskTick = RegInit(0.U(64.W))
  private val outputHold = RegInit(0.U(3.W))
  private val completionHold = RegInit(0.U(3.W))
  private val golden = state === State.SendTask || state === State.SendInput ||
    state === State.WaitCompletion
  private val probeTag = "hffff0000".U(32.W) | probe
  private val negativeTag = "hffff0100".U(32.W) | negative
  private val frameTag = frame +& 1.U
  private val finalInput = inputs === (cfg.beats - 1).U
  private val finalOutput = outputs === (cfg.beats - 1).U

  for (rom <- Seq(inputRom, expectedRom)) {
    rom.io.start := state === State.StartFrame
    rom.io.base := frame << (cfg.logN - 1)
    rom.io.length := cfg.beats.U
    rom.io.out.ready := false.B
  }
  for ((rom, direction) <- Seq((forwardRom, false), (inverseRom, true))) {
    rom.io.start := state === State.StartTable && inverseTable === direction.B
    rom.io.base := 0.U
    rom.io.length := cfg.beats.U
    rom.io.out.ready := state === State.LoadTable &&
      inverseTable === direction.B && io.dut.table.ready
  }
  private val tableValid = Mux(inverseTable, inverseRom.io.out.valid, forwardRom.io.out.valid)
  private val tableData = Mux(inverseTable, inverseRom.io.out.bits.data, forwardRom.io.out.bits.data)
  private val tableLast = Mux(inverseTable, inverseRom.io.out.bits.last, forwardRom.io.out.bits.last)
  private val tableBuffered = Mux(inverseTable, inverseRom.io.buffered, forwardRom.io.buffered)

  io.dut.task.valid := state === State.SendTask || state === State.PreTask ||
    state === State.NegativeTask || state === State.BadEarlyTable
  io.dut.task.bits.inverse := Mux(state === State.PreTask || state === State.BadEarlyTable, probe(0),
    Mux(state === State.NegativeTask, negative(0), frame(0)))
  io.dut.task.bits.tag := Mux(state === State.PreTask || state === State.BadEarlyTable, probeTag,
    Mux(state === State.NegativeTask, negativeTag, frameTag))
  io.dut.table.valid := (state === State.LoadTable && tableValid) ||
    state === State.BadEarlyTable || state === State.BadCanonicalTable
  io.dut.table.bits.inverse := Mux(state === State.BadEarlyTable, false.B,
    Mux(state === State.BadCanonicalTable, true.B, inverseTable))
  io.dut.table.bits.data := Mux(state === State.BadEarlyTable, 1.U,
    Mux(state === State.BadCanonicalTable, Mux(badBeat === 0.U, modulus, 1.U), tableData))
  io.dut.table.bits.last := Mux(state === State.BadEarlyTable, true.B,
    Mux(state === State.BadCanonicalTable, badBeat === (cfg.beats - 1).U, tableLast))
  io.dut.input.valid := (state === State.SendInput && inputRom.io.out.valid) ||
    state === State.NegativeInput
  io.dut.input.bits.data(0) := Mux(state === State.NegativeInput,
    Mux(negative === 2.U && badBeat === 0.U, modulus, 0.U), inputRom.io.out.bits.data(63, 0))
  io.dut.input.bits.data(1) := Mux(state === State.NegativeInput, 0.U,
    inputRom.io.out.bits.data(127, 64))
  io.dut.input.bits.last := Mux(state === State.NegativeInput,
    Mux(negative === 0.U, badBeat === 0.U || badBeat === (cfg.beats - 1).U,
      Mux(negative === 1.U, false.B, badBeat === (cfg.beats - 1).U)),
    inputRom.io.out.bits.last)
  inputRom.io.out.ready := state === State.SendInput && io.dut.input.ready

  // Ideal mode NEVER gates output/completion ready on reference fetches. The
  // expected FIFO is already full before task acceptance and refills at II=1.
  private val outputPacing = if (variedPacing)
    outputHold === 3.U && tick(2, 0) =/= 0.U && tick(2, 0) =/= 1.U else true.B
  private val completionPacing = if (variedPacing) completionHold === 5.U else true.B
  io.dut.output.ready := active && outputPacing
  io.dut.completion.ready := active && completionPacing
  expectedRom.io.out.ready := golden && outputs < cfg.beats.U && io.dut.output.fire
  when(io.dut.task.fire) {
    outputHold := 0.U
    completionHold := 0.U
  }
  if (variedPacing) {
    when(active && io.dut.output.valid && outputHold < 3.U) { outputHold := outputHold + 1.U }
    when(active && io.dut.completion.valid && completionHold < 5.U) {
      completionHold := completionHold + 1.U
    }
  }

  // Check valid and complete payload stability throughout consumer stalls.
  private val outputBlocked = RegNext(io.dut.output.valid && !io.dut.output.ready, false.B)
  private val heldOutput = RegEnable(io.dut.output.bits.asUInt,
    io.dut.output.valid && !io.dut.output.ready)
  private val completionBlocked = RegNext(io.dut.completion.valid && !io.dut.completion.ready, false.B)
  private val heldCompletion = RegEnable(io.dut.completion.bits.asUInt,
    io.dut.completion.valid && !io.dut.completion.ready)
  private val outputFire = io.dut.output.fire
  private val completionFire = io.dut.completion.fire
  private val goldenOutput = golden && outputFire
  private val goldenCompletion = state === State.WaitCompletion && completionFire
  private val preCompletion = state === State.PreWait && completionFire
  private val negativeCompletion = state === State.NegativeWait && completionFire
  private val expectedStatus = Mux(state === State.PreWait, 1.U,
    Mux(state === State.NegativeWait, 2.U, 0.U))
  private val expectedTag = Mux(state === State.PreWait, probeTag,
    Mux(state === State.NegativeWait, negativeTag, frameTag))
  private val expectedInverse = Mux(state === State.PreWait, probe(0),
    Mux(state === State.NegativeWait, negative(0), frame(0)))
  private val errors = Seq(
    active && outputBlocked && (!io.dut.output.valid || io.dut.output.bits.asUInt =/= heldOutput),
    active && completionBlocked && (!io.dut.completion.valid || io.dut.completion.bits.asUInt =/= heldCompletion),
    outputFire && !golden,
    goldenOutput && (inputs =/= cfg.beats.U || !firstInputSeen),
    goldenOutput && (outputs >= cfg.beats.U || !expectedRom.io.out.valid),
    goldenOutput && io.dut.output.bits.last =/= finalOutput,
    goldenOutput && outputs < cfg.beats.U && expectedRom.io.out.valid &&
      io.dut.output.bits.data(0) =/= expectedRom.io.out.bits.data(63, 0),
    goldenOutput && outputs < cfg.beats.U && expectedRom.io.out.valid &&
      io.dut.output.bits.data(1) =/= expectedRom.io.out.bits.data(127, 64),
    golden && io.dut.completion.valid && outputs =/= cfg.beats.U,
    state === State.NegativeInput && io.dut.completion.valid,
    completionFire && !(goldenCompletion || preCompletion || negativeCompletion),
    completionFire && io.dut.completion.bits.status =/= expectedStatus,
    completionFire && io.dut.completion.bits.tag =/= expectedTag,
    completionFire && io.dut.completion.bits.inverse =/= expectedInverse,
    (preCompletion || negativeCompletion) && io.dut.completion.bits.computeCycles =/= 0.U,
    goldenCompletion && (inputs =/= cfg.beats.U || outputs =/= cfg.beats.U || !firstInputSeen),
    goldenCompletion && io.dut.completion.bits.computeCycles === 0.U,
    (state === State.PreTask || state === State.PreWait) && io.dut.input.ready,
    state === State.BadTableCheck && io.dut.configured =/= 0.U,
    state === State.Finish && checked =/= (cfg.n.toLong * Frames).U,
    state === State.Finish && completed =/= Frames.U,
    active && tick === (TimeoutCycles - 1).U
  )
  private val errorCount = PopCount(VecInit(errors))
  when(errorCount =/= 0.U) { mismatches := mismatches + errorCount }
  when(goldenOutput) {
    checked := checked + 2.U
    outputs := outputs + 1.U
    when(finalOutput) { frameTime(frame) := tick - firstInputTick + 1.U }
  }
  when(state === State.SendInput && io.dut.input.fire) {
    assert(io.dut.input.bits.last === finalInput, "ENV input last/count disagree")
    when(!firstInputSeen) {
      firstInputSeen := true.B
      firstInputTick := tick
    }
    inputs := inputs + 1.U
    when(finalInput) { state := State.WaitCompletion }
  }
  if (!variedPacing) {
    when(state === State.SendInput && io.dut.input.ready) {
      assert(io.dut.input.valid, "ideal ENV input bubble")
    }
  }
  when(goldenOutput && outputs < cfg.beats.U) {
    assert(expectedRom.io.out.valid, "ENV expected SRAM response underflow")
  }

  when(active) { tick := tick + 1.U }
  switch(state) {
    is(State.Idle) {
      when(io.trigger) {
        tick := 1.U
        state := (if (variedPacing) State.PreTask else State.StartTable)
      }
    }
    is(State.PreTask) { when(io.dut.task.fire) { state := State.PreWait } }
    is(State.PreWait) {
      when(completionFire) {
        probe := probe + 1.U
        when(probe === 0.U) { state := State.PreTask }
          .elsewhen(probe === 1.U) { state := State.BadEarlyTable }
          .elsewhen(probe === 2.U) { badBeat := 0.U; state := State.BadCanonicalTable }
          .otherwise { state := State.StartTable }
      }
    }
    is(State.BadEarlyTable) {
      // 新版同时有效控制入口：两拍均保存，先提交错误表，再报告缺表任务。
      when(io.dut.table.fire) {
        assert(io.dut.task.fire, "concurrent control ingress was not accepted")
        state := State.PreWait
      }
    }
    is(State.BadCanonicalTable) {
      when(io.dut.table.fire) {
        badBeat := badBeat + 1.U
        when(badBeat === (cfg.beats - 1).U) { state := State.BadTableCheck }
      }
    }
    is(State.BadTableCheck) { state := State.PreTask }
    is(State.StartTable) { state := State.TablePrefetch }
    is(State.TablePrefetch) { when(tableBuffered === 2.U) { state := State.LoadTable } }
    is(State.LoadTable) { when(io.dut.table.fire && tableLast) { state := State.CheckTable } }
    is(State.CheckTable) {
      // 新版入口表拍先缓冲；必须等待实际提交状态，而非最后外部握手即认为生效。
      when(io.dut.configured === Mux(inverseTable, 3.U, 1.U)) {
        when(!inverseTable) { inverseTable := true.B; state := State.StartTable }
          .otherwise { state := State.StartFrame }
      }
    }
    is(State.StartFrame) {
      inputs := 0.U
      outputs := 0.U
      firstInputSeen := false.B
      state := State.FramePrefetch
    }
    is(State.FramePrefetch) {
      when(inputRom.io.buffered === 2.U && expectedRom.io.buffered === 2.U) {
        state := State.SendTask
      }
    }
    is(State.SendTask) {
      when(io.dut.task.fire) { taskTick := tick; state := State.SendInput }
    }
    is(State.WaitCompletion) {
      when(completionFire) {
        compute(frame) := io.dut.completion.bits.computeCycles
        taskTime(frame) := tick - taskTick + 1.U
        completed := completed + 1.U
        when(frame === (Frames - 1).U) {
          state := (if (variedPacing) State.NegativeTask else State.Finish)
        }.otherwise { frame := frame + 1.U; state := State.StartFrame }
      }
    }
    is(State.NegativeTask) {
      when(io.dut.task.fire) { badBeat := 0.U; state := State.NegativeInput }
    }
    is(State.NegativeInput) {
      when(io.dut.input.fire) {
        badBeat := badBeat + 1.U
        when(badBeat === (cfg.beats - 1).U) { state := State.NegativeWait }
      }
    }
    is(State.NegativeWait) {
      when(completionFire) {
        when(negative === 2.U) { state := State.Finish }
          .otherwise { negative := negative + 1.U; state := State.NegativeTask }
      }
    }
    is(State.Finish) { state := State.Done }
  }
  when(active && tick === (TimeoutCycles - 1).U) {
    timeout := true.B
    state := State.Done
  }
  io.done := state === State.Done
  io.pass := io.done && !timeout && mismatches === 0.U &&
    checked === (cfg.n.toLong * Frames).U && completed === Frames.U
  io.timedOut := timeout
  io.mismatchCount := mismatches
  io.cycles := tick
  io.checkedWords := checked
  io.framesCompleted := completed
  io.computeCycles := compute
  io.frameCycles := frameTime
  io.taskCycles := taskTime
}

/** Verilator host may drive only clock, reset and a one-shot trigger. All ROMs
  * and readmemh annotations are below ENV, never in the product DUT.
  */
class AsicNttPairSimTop(cfg: AsicNttConfig, vectorDirectory: String,
    variedPacing: Boolean = false) extends RawModule {
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
    val dut = Module(new AsicGoldilocksNttPair(cfg))
    val env = Module(new AsicNttPairEnv(cfg, vectorDirectory, variedPacing))
    env.io.trigger := trigger
    env.io.dut <> dut.io
    done := env.io.done
    pass := env.io.pass
    timedOut := env.io.timedOut
    mismatchCount := env.io.mismatchCount
    cycles := env.io.cycles
    checkedWords := env.io.checkedWords
    framesCompleted := env.io.framesCompleted
    computeCycles := env.io.computeCycles
    frameCycles := env.io.frameCycles
    taskCycles := env.io.taskCycles
  }
}
