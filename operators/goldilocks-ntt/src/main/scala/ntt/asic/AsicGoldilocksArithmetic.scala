/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks NTT/INTT Hardware Accelerator
 * Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
 * Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
 * File: operators/goldilocks-ntt/src/main/scala/ntt/asic/AsicGoldilocksArithmetic.scala
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

/** 可移植的倍增前缀进位；每一层合并2^k位，避免线性宽进位链。
  * 结果显式截断到 width 位；调用者为需要进位的运算多保留一位。
  */
object AsicPrefixArithmetic {
  def add(a: UInt, b: UInt, width: Int, carryIn: Bool = false.B): UInt = {
    require(width >= 2)
    val left = Wire(UInt(width.W)); left := a
    val right = Wire(UInt(width.W)); right := b
    val bits = left ^ right
    var propagate: UInt = bits
    var generate: UInt = left & right
    var distance = 1
    while (distance < width) {
      val shiftedGenerate = (generate << distance)(width - 1, 0)
      val shiftedPropagate = Cat(propagate(width - distance - 1, 0),
        ((BigInt(1) << distance) - 1).U(distance.W))
      val nextGenerate = generate | (propagate & shiftedGenerate)
      val nextPropagate = propagate & shiftedPropagate
      generate = nextGenerate
      propagate = nextPropagate
      distance *= 2
    }
    val carries = ((generate << 1) | ((propagate << 1) & Fill(width + 1, carryIn)) |
      carryIn.asUInt)(width - 1, 0)
    bits ^ carries
  }
  def subtract(a: UInt, b: UInt, width: Int): UInt = {
    val right = Wire(UInt(width.W)); right := b
    add(a, ~right, width, true.B)
  }
}

object AsicGoldilocksMul {
  val Latency = 11
}

/** Standard-cell-oriented arithmetic. Four 32x32 products, not FPGA DSP limbs.
  * Fixed eleven-cycle latency and II=1; physical frequency belongs to DC owner.
  */
class AsicGoldilocksMul extends Module {
  val io = IO(new Bundle {
    val a = Input(UInt(64.W))
    val b = Input(UInt(64.W))
    val product = Output(UInt(64.W))
  })
  private val p = GoldilocksReference.Modulus.U(66.W)
  private val p0 = RegNext(io.a(31, 0) * io.b(31, 0))
  private val p1 = RegNext(io.a(31, 0) * io.b(63, 32))
  private val p2 = RegNext(io.a(63, 32) * io.b(31, 0))
  private val p3 = RegNext(io.a(63, 32) * io.b(63, 32))
  private val outerLow = RegNext(p0)
  private val cross = RegNext(AsicPrefixArithmetic.add(p1, p2, 65))
  private val outerHigh = RegNext(p3)
  private val low = RegNext(AsicPrefixArithmetic.add(outerLow, Cat(cross(31, 0), 0.U(32.W)), 65))
  private val crossHigh = RegNext(cross(64, 32))
  private val highDelayed = RegNext(outerHigh)
  private val productLow = RegNext(low(63, 0))
  private val productHigh = RegNext(AsicPrefixArithmetic.add(
    Cat(0.U(1.W), highDelayed), Cat(0.U(32.W), crossHigh), 65, low(64)))
  private val t0 = RegNext(productLow(31, 0))
  private val highSum = RegNext(AsicPrefixArithmetic.add(productLow(63, 32), productHigh(31, 0), 33))
  private val subtrahend = RegNext(AsicPrefixArithmetic.add(productHigh(31, 0), productHigh(63, 32), 33))
  private val positive = RegNext(AsicPrefixArithmetic.add(Cat(0.U(1.W), highSum, t0), p, 66))
  private val delayedSub = RegNext(subtrahend)
  private val candidate = RegNext(AsicPrefixArithmetic.subtract(positive, Cat(0.U(33.W), delayedSub), 66))
  private val candidateDelay = RegNext(candidate)
  private val firstSubtract = RegNext(candidate >= p)
  private val once = RegNext(Mux(firstSubtract, AsicPrefixArithmetic.subtract(candidateDelay, p, 66), candidateDelay))
  private val onceDelay = RegNext(once)
  private val secondSubtract = RegNext(once >= p)
  private val twice = RegNext(Mux(secondSubtract, AsicPrefixArithmetic.subtract(onceDelay, p, 66), onceDelay))
  io.product := twice(63, 0)
}

object AsicGoldilocksButterfly {
  val Latency = AsicGoldilocksMul.Latency + 6
}

class AsicGoldilocksButterfly extends Module {
  val io = IO(new Bundle {
    val valid = Input(Bool())
    val inverse = Input(Bool())
    val a = Input(UInt(64.W))
    val b = Input(UInt(64.W))
    val twiddle = Input(UInt(64.W))
    val outValid = Output(Bool())
    val out0 = Output(UInt(64.W))
    val out1 = Output(UInt(64.W))
  })
  private val p = GoldilocksReference.Modulus.U(65.W)
  private val multiplier = Module(new AsicGoldilocksMul)
  multiplier.io.a := io.b
  multiplier.io.b := io.twiddle
  private val a = ShiftRegister(io.a, AsicGoldilocksMul.Latency)
  private val sum = RegNext(AsicPrefixArithmetic.add(a, multiplier.io.product, 65))
  private val differenceBase = RegNext(AsicPrefixArithmetic.add(Cat(0.U(1.W), a), p, 65))
  private val product = RegNext(multiplier.io.product)
  private val sumDelay = RegNext(sum)
  private val sumSubtract = RegNext(sum >= p)
  private val difference = RegNext(AsicPrefixArithmetic.subtract(differenceBase, product, 65))
  private val sumCorrect = RegNext(Mux(sumSubtract, AsicPrefixArithmetic.subtract(sumDelay, p, 65), sumDelay))
  private val differenceDelay = RegNext(difference)
  private val differenceSubtract = RegNext(difference >= p)
  private val result0 = RegNext(sumCorrect(63, 0))
  private val result1 = RegNext(Mux(differenceSubtract,
    AsicPrefixArithmetic.subtract(differenceDelay, p, 65), differenceDelay)(63, 0))
  private val inverse15 = ShiftRegister(io.inverse, AsicGoldilocksMul.Latency + 4)
  private val inverse16 = ShiftRegister(io.inverse, AsicGoldilocksButterfly.Latency - 1)
  private def half(value: UInt): UInt = {
    val extended = RegNext(Mux(inverse15 && value(0),
      AsicPrefixArithmetic.add(Cat(0.U(1.W), value), p, 65), Cat(0.U(1.W), value)))
    RegNext(Mux(inverse16, extended(64, 1), extended(63, 0)))
  }
  io.out0 := half(result0)
  io.out1 := half(result1)
  io.outValid := ShiftRegister(io.valid, AsicGoldilocksButterfly.Latency, false.B, true.B)
}
