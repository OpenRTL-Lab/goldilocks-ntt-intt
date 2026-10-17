/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks NTT/INTT Hardware Accelerator
 * Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
 * Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
 * File: operators/goldilocks-ntt/src/main/scala/ntt/asic/AsicNttPair.scala
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
import ntt.GoldilocksReference

final case class AsicNttConfig(logN: Int) {
  require(logN >= 4 && logN <= 16, "supported logN is 4..16")
  val n: Int = 1 << logN
  val beats: Int = n / 2
}

class AsicNttTask extends Bundle {
  val inverse = Bool()
  val tag = UInt(32.W)
}
class AsicNttTableBeat extends Bundle {
  val inverse = Bool()
  val data = UInt(64.W)
  val last = Bool()
}
class AsicNttDataBeat extends Bundle {
  val data = Vec(2, UInt(64.W))
  val last = Bool()
}
class AsicNttCompletion extends Bundle {
  val inverse = Bool()
  val tag = UInt(32.W)
  // 0=OK, 1=direction table not configured, 2=malformed/noncanonical frame.
  val status = UInt(2.W)
  val computeCycles = UInt(64.W)
}
class AsicNttPairIO extends Bundle {
  val task = Flipped(Decoupled(new AsicNttTask))
  val table = Flipped(Decoupled(new AsicNttTableBeat))
  val input = Flipped(Decoupled(new AsicNttDataBeat))
  val output = Decoupled(new AsicNttDataBeat)
  val completion = Decoupled(new AsicNttCompletion)
  val configured = Output(UInt(2.W))
}

/** Portable latency-one 1R1W SRAM contract; owner maps these to ASIC macros.
  * No memory reset/init, FPGA primitive, placement or synthesis attribute.
  */
class AsicSram1R1W(val rows: Int) extends Module {
  require(rows >= 2 && isPow2(rows))
  override def desiredName: String = s"AsicSram1R1W_${rows}x64"
  val io = IO(new Bundle {
    val readEnable = Input(Bool())
    val readAddress = Input(UInt(log2Ceil(rows).W))
    val readData = Output(UInt(64.W))
    val writeEnable = Input(Bool())
    val writeAddress = Input(UInt(log2Ceil(rows).W))
    val writeData = Input(UInt(64.W))
  })
  private val memory = SyncReadMem(rows, UInt(64.W))
  io.readData := memory.read(io.readAddress, io.readEnable)
  when(io.writeEnable) { memory.write(io.writeAddress, io.writeData) }
}

/** XOR bank mapping supports any radix-2 stride with one read/write per bank. */
class AsicNttPlane(val cfg: AsicNttConfig) extends Module {
  val io = IO(new Bundle {
    val readEnable = Input(Bool())
    val readIndex = Input(Vec(2, UInt(cfg.logN.W)))
    val readData = Output(Vec(2, UInt(64.W)))
    val writeEnable = Input(Bool())
    val writeIndex = Input(Vec(2, UInt(cfg.logN.W)))
    val writeData = Input(Vec(2, UInt(64.W)))
  })
  private val banks = Seq.fill(2)(Module(new AsicSram1R1W(cfg.beats)))
  private val readBank = io.readIndex.map(_.xorR)
  private val writeBank = io.writeIndex.map(_.xorR)
  when(io.readEnable) { assert(readBank(0) =/= readBank(1), "read bank collision") }
  when(io.writeEnable) { assert(writeBank(0) =/= writeBank(1), "write bank collision") }
  for (bank <- 0 until 2) {
    banks(bank).io.readEnable := io.readEnable
    banks(bank).io.readAddress := Mux(readBank(0) === (bank == 1).B,
      io.readIndex(0)(cfg.logN - 1, 1), io.readIndex(1)(cfg.logN - 1, 1))
    banks(bank).io.writeEnable := io.writeEnable
    banks(bank).io.writeAddress := Mux(writeBank(0) === (bank == 1).B,
      io.writeIndex(0)(cfg.logN - 1, 1), io.writeIndex(1)(cfg.logN - 1, 1))
    banks(bank).io.writeData := Mux(writeBank(0) === (bank == 1).B,
      io.writeData(0), io.writeData(1))
  }
  for (word <- 0 until 2) {
    io.readData(word) := Mux(RegNext(readBank(word)),
      banks(1).io.readData, banks(0).io.readData)
  }
}

/** One independently generated bidirectional operator, not a SoC shell. */
class AsicGoldilocksNttPair(val cfg: AsicNttConfig) extends Module {
  val io = IO(new AsicNttPairIO)
  private val p = GoldilocksReference.Modulus.U(64.W)
  object State extends ChiselEnum {
    val Idle, Load, Issue, Drain, Output, Complete = Value
  }
  private val state = RegInit(State.Idle)
  // 外部入口不直通，不让 valid/data/ready 跨越两个 IO 延迟预算。
  private val taskQueue = Module(new Queue(new AsicNttTask, 1, pipe = false, flow = false))
  private val tableQueue = Module(new Queue(new AsicNttTableBeat, 2, pipe = false, flow = false))
  private val inputQueue = Module(new Queue(new AsicNttDataBeat, 2, pipe = false, flow = false))
  private val inputAccepted = RegInit(0.U((cfg.logN - 1).W))
  private val inputAllAccepted = RegInit(false.B)
  taskQueue.io.enq.bits := io.task.bits
  taskQueue.io.enq.valid := io.task.valid && state === State.Idle
  io.task.ready := taskQueue.io.enq.ready && state === State.Idle
  tableQueue.io.enq.bits := io.table.bits
  tableQueue.io.enq.valid := io.table.valid && state === State.Idle
  io.table.ready := tableQueue.io.enq.ready && state === State.Idle
  inputQueue.io.enq.bits := io.input.bits
  inputQueue.io.enq.valid := io.input.valid && state === State.Load && !inputAllAccepted
  io.input.ready := inputQueue.io.enq.ready && state === State.Load && !inputAllAccepted
  when(io.input.fire) {
    inputAccepted := inputAccepted + 1.U
    when(inputAccepted === (cfg.beats - 1).U) { inputAllAccepted := true.B }
  }
  private val taskIn = taskQueue.io.deq
  private val tableIn = tableQueue.io.deq
  private val inputIn = inputQueue.io.deq
  tableIn.ready := state === State.Idle
  taskIn.ready := state === State.Idle && !tableIn.valid
  inputIn.ready := state === State.Load
  private val active = Reg(new AsicNttTask)
  private val status = RegInit(0.U(2.W))
  private val computeCycles = RegInit(0.U(64.W))
  private val loadBeat = RegInit(0.U((cfg.logN - 1).W))
  private val stage = RegInit(0.U(log2Ceil(cfg.logN).W))
  private val butterflyIndex = RegInit(0.U((cfg.logN - 1).W))
  private val sourcePlane = RegInit(false.B)
  private val outputBeat = RegInit(0.U((cfg.logN - 1).W))
  private val outputAllIssued = RegInit(false.B)
  private val planes = Seq.fill(2)(Module(new AsicNttPlane(cfg)))
  private val table = Module(new AsicSram1R1W(cfg.n))
  private val configured = RegInit(VecInit(Seq.fill(2)(false.B)))
  private val tablePosition = RegInit(VecInit(Seq.fill(2)(0.U((cfg.logN - 1).W))))
  private val tableGood = RegInit(VecInit(Seq.fill(2)(true.B)))
  private val direction = tableIn.bits.inverse.asUInt
  private val tableLastExpected = tablePosition(direction) === (cfg.beats - 1).U
  io.configured := configured.asUInt
  table.io.writeEnable := tableIn.fire && tableIn.bits.data < p
  table.io.writeAddress := Cat(tableIn.bits.inverse, tablePosition(direction))
  table.io.writeData := tableIn.bits.data
  when(tableIn.fire) {
    configured(direction) := false.B
    val canonical = tableIn.bits.data < p
    when(tableIn.bits.last || tableLastExpected) {
      configured(direction) := tableGood(direction) && canonical &&
        tableIn.bits.last && tableLastExpected
      tablePosition(direction) := 0.U
      tableGood(direction) := true.B
    }.otherwise {
      tablePosition(direction) := tablePosition(direction) + 1.U
      tableGood(direction) := tableGood(direction) && canonical
    }
  }

  io.completion.valid := state === State.Complete
  io.completion.bits.inverse := active.inverse
  io.completion.bits.tag := active.tag
  io.completion.bits.status := status
  io.completion.bits.computeCycles := computeCycles
  when(taskIn.fire) {
    inputAccepted := 0.U
    inputAllAccepted := false.B
    active := taskIn.bits
    status := Mux(configured(taskIn.bits.inverse.asUInt), 0.U, 1.U)
    computeCycles := 0.U
    loadBeat := 0.U
    stage := 0.U
    butterflyIndex := 0.U
    sourcePlane := false.B
    outputBeat := 0.U
    outputAllIssued := false.B
    state := Mux(configured(taskIn.bits.inverse.asUInt), State.Load, State.Complete)
  }
  private val loadLast = loadBeat === (cfg.beats - 1).U
  private val badInput = inputIn.bits.last =/= loadLast ||
    inputIn.bits.data.map(_ >= p).reduce(_ || _)
  when(inputIn.fire) {
    when(badInput) { status := 2.U }
    loadBeat := loadBeat + 1.U
    when(loadLast) {
      state := Mux(status =/= 0.U || badInput, State.Complete, State.Issue)
    }
  }
  when(state === State.Issue || state === State.Drain) {
    computeCycles := computeCycles + 1.U
  }

  private val issue = state === State.Issue
  private val spanLog = (cfg.logN - 1).U - stage
  private val span = (1.U(cfg.logN.W) << spanLog)(cfg.logN - 1, 0)
  private val mask = span - 1.U
  private val index = Cat(0.U(1.W), butterflyIndex)
  private val groupBase = index & ~mask
  private val source0 = ((groupBase << 1) | (index & mask))(cfg.logN - 1, 0)
  private val source1 = source0 | span
  private val issueLast = butterflyIndex === (cfg.beats - 1).U
  table.io.readEnable := issue
  table.io.readAddress := Cat(active.inverse, groupBase(cfg.logN - 2, 0))
  when(issue) {
    butterflyIndex := butterflyIndex + 1.U
    when(issueLast) { state := State.Drain }
  }
  private val butterfly = Module(new AsicGoldilocksButterfly)
  butterfly.io.valid := ShiftRegister(issue, 2, false.B, true.B)
  butterfly.io.inverse := active.inverse
  butterfly.io.a := RegNext(Mux(sourcePlane, planes(1).io.readData(0), planes(0).io.readData(0)))
  butterfly.io.b := RegNext(Mux(sourcePlane, planes(1).io.readData(1), planes(0).io.readData(1)))
  butterfly.io.twiddle := RegNext(table.io.readData)
  private val writeIndex = ShiftRegister(butterflyIndex,
    2 + AsicGoldilocksButterfly.Latency)
  private val writeLast = ShiftRegister(issue && issueLast,
    2 + AsicGoldilocksButterfly.Latency, false.B, true.B)
  when(butterfly.io.outValid && writeLast) {
    sourcePlane := !sourcePlane
    butterflyIndex := 0.U
    when(stage === (cfg.logN - 1).U) {
      state := State.Output
    }.otherwise {
      stage := stage + 1.U
      state := State.Issue
    }
  }

  // Queue capacity reserves the synchronous SRAM response, avoiding either
  // overwriting held output or artificially inserting ideal-output bubbles.
  private val queue = Module(new Queue(new AsicNttDataBeat, 4, pipe = false, flow = false))
  io.output <> queue.io.deq
  private val pendingOutput = RegInit(false.B)
  private val canReadOutput = (queue.io.count +& pendingOutput.asUInt) < 4.U
  private val outputRead = state === State.Output && !outputAllIssued && canReadOutput
  pendingOutput := outputRead
  queue.io.enq.valid := pendingOutput
  queue.io.enq.bits.data := Mux(sourcePlane, planes(1).io.readData, planes(0).io.readData)
  queue.io.enq.bits.last := RegNext(outputBeat === (cfg.beats - 1).U)
  when(pendingOutput) { assert(queue.io.enq.ready, "reserved output slot lost") }
  when(outputRead) {
    outputBeat := outputBeat + 1.U
    when(outputBeat === (cfg.beats - 1).U) { outputAllIssued := true.B }
  }
  when(io.output.fire && io.output.bits.last) { state := State.Complete }
  when(io.completion.fire) { state := State.Idle }

  for (plane <- 0 until 2) {
    val isSource = sourcePlane === (plane == 1).B
    planes(plane).io.readEnable := isSource && (issue || outputRead)
    planes(plane).io.readIndex(0) := Mux(outputRead, Cat(outputBeat, 0.U(1.W)), source0)
    planes(plane).io.readIndex(1) := Mux(outputRead, Cat(outputBeat, 1.U(1.W)), source1)
    val loading = inputIn.fire && (plane == 0).B
    val writing = butterfly.io.outValid && !isSource
    planes(plane).io.writeEnable := loading || writing
    planes(plane).io.writeIndex(0) := Mux(loading, Cat(loadBeat, 0.U(1.W)), Cat(0.U(1.W), writeIndex))
    planes(plane).io.writeIndex(1) := Mux(loading, Cat(loadBeat, 1.U(1.W)), Cat(1.U(1.W), writeIndex))
    planes(plane).io.writeData(0) := Mux(loading, inputIn.bits.data(0), butterfly.io.out0)
    planes(plane).io.writeData(1) := Mux(loading, inputIn.bits.data(1), butterfly.io.out1)
  }
}
