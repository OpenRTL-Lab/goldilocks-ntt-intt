/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks NTT/INTT Hardware Accelerator
 * Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
 * Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
 * File: common/goldilocks/src/main/scala/ntt/GoldilocksReference.scala
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

package ntt

/** Elaboration-time and audit-only mathematical reference.
  *
  * BigInt remainder is intentionally confined to Scala software. None of this
  * object's arithmetic is emitted as synthesizable hardware.
  */
object GoldilocksReference {
  val Modulus: BigInt = (BigInt(1) << 64) - (BigInt(1) << 32) + 1
  val CosetShift: BigInt = 7

  /** Exact Proofman Goldilocks::W roots for domains 2^0 through 2^16.
    *
    * Seven is Proofman's LDE coset shift, not the root-table generator.
    */
  val ProofmanRoots: Vector[BigInt] = Vector(
    BigInt("1"),
    BigInt("18446744069414584320"),
    BigInt("281474976710656"),
    BigInt("16777216"),
    BigInt("4096"),
    BigInt("64"),
    BigInt("8"),
    BigInt("2198989700608"),
    BigInt("4404853092538523347"),
    BigInt("6434636298004421797"),
    BigInt("4255134452441852017"),
    BigInt("9113133275150391358"),
    BigInt("4355325209153869931"),
    BigInt("4308460244895131701"),
    BigInt("7126024226993609386"),
    BigInt("1873558160482552414"),
    BigInt("8167150655112846419"),
  )

  def canonical(value: BigInt): BigInt = {
    val reduced = value % Modulus
    if (reduced.signum < 0) reduced + Modulus else reduced
  }

  def add(a: BigInt, b: BigInt): BigInt = canonical(a + b)
  def sub(a: BigInt, b: BigInt): BigInt = canonical(a - b)
  def mul(a: BigInt, b: BigInt): BigInt = canonical(a * b)
  def pow(a: BigInt, exponent: BigInt): BigInt = a.modPow(exponent, Modulus)
  def inverse(a: BigInt): BigInt = a.modInverse(Modulus)

  def requirePowerOfTwo(n: Int): Unit =
    require(n >= 2 && (n & (n - 1)) == 0, s"N must be a power of two: $n")

  def proofmanRoot(n: Int): BigInt = {
    requirePowerOfTwo(n)
    val logN = Integer.numberOfTrailingZeros(n)
    require(logN < ProofmanRoots.length, s"unsupported Proofman root: 2^$logN")
    ProofmanRoots(logN)
  }

  // Compatibility name for the v0.1 scaffold source.  It now returns the
  // authoritative Proofman root rather than deriving a root from shift=7.
  def primitiveRoot(n: Int): BigInt = proofmanRoot(n)

  def bitReverse(value: Int, width: Int): Int = {
    var source = value
    var result = 0
    for (_ <- 0 until width) {
      result = (result << 1) | (source & 1)
      source = source >>> 1
    }
    result
  }

  /** Independent direct O(N^2) definition used for expected test vectors. */
  def directTransform(input: Seq[BigInt], inverseTransform: Boolean): Vector[BigInt] = {
    val n = input.length
    requirePowerOfTwo(n)
    val forwardRoot = proofmanRoot(n)
    val root = if (inverseTransform) inverse(forwardRoot) else forwardRoot
    val scale = if (inverseTransform) inverse(BigInt(n)) else BigInt(1)
    Vector.tabulate(n) { k =>
      val sum = input.indices.foldLeft(BigInt(0)) { (accumulator, j) =>
        val twiddle = pow(root, BigInt(j) * BigInt(k))
        add(accumulator, mul(canonical(input(j)), twiddle))
      }
      mul(sum, scale)
    }
  }

  /** Iterative model mirrors the scheduling family but not the RTL helpers. */
  def iterativeTransform(input: Seq[BigInt], inverseTransform: Boolean): Vector[BigInt] = {
    val n = input.length
    requirePowerOfTwo(n)
    val width = Integer.numberOfTrailingZeros(n)
    val data = input.map(canonical).toArray
    for (index <- 0 until n) {
      val reversed = bitReverse(index, width)
      if (reversed > index) {
        val temporary = data(index)
        data(index) = data(reversed)
        data(reversed) = temporary
      }
    }
    val forwardRoot = proofmanRoot(n)
    val root = if (inverseTransform) inverse(forwardRoot) else forwardRoot
    var length = 2
    while (length <= n) {
      val stageRoot = pow(root, n / length)
      var base = 0
      while (base < n) {
        var twiddle = BigInt(1)
        var offset = 0
        while (offset < length / 2) {
          val a = data(base + offset)
          val t = mul(data(base + offset + length / 2), twiddle)
          data(base + offset) = add(a, t)
          data(base + offset + length / 2) = sub(a, t)
          twiddle = mul(twiddle, stageRoot)
          offset += 1
        }
        base += length
      }
      length *= 2
    }
    if (inverseTransform) {
      val scale = inverse(BigInt(n))
      for (index <- data.indices) data(index) = mul(data(index), scale)
    }
    data.toVector
  }

  /** Proofman LDE: INTT(N), coefficient coset shift, zero extension, NTT(NExt). */
  def lde(input: Seq[BigInt], extendedSize: Int): Vector[BigInt] = {
    val n = input.length
    requirePowerOfTwo(n)
    requirePowerOfTwo(extendedSize)
    require(extendedSize > n && extendedSize % n == 0)
    val coefficients = iterativeTransform(input, inverseTransform = true)
    var shiftPower = BigInt(1)
    val shifted = coefficients.map { coefficient =>
      val value = mul(coefficient, shiftPower)
      shiftPower = mul(shiftPower, CosetShift)
      value
    }
    iterativeTransform(
      shifted ++ Vector.fill(extendedSize - n)(BigInt(0)),
      inverseTransform = false,
    )
  }

  val VerificationInput: Vector[BigInt] = Vector(
    0,
    1,
    2,
    3,
    Modulus - 1,
    Modulus - 2,
    BigInt("0123456789abcdef", 16),
    BigInt("fedcba9876543210", 16) % Modulus,
    BigInt(1) << 32,
    (BigInt(1) << 32) - 1,
    (BigInt(1) << 63) - 1,
    BigInt(1) << 63,
    BigInt("deadbeefcafebabe", 16) % Modulus,
    BigInt("1111222233334444", 16),
    BigInt("5555666677778888", 16),
    BigInt("9999aaaabbbbcccc", 16),
  )
}
