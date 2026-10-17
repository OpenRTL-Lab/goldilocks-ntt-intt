/*
 * OpenRTL-Header-Begin
 * OpenRTL — Open-source hardware accelerators.
 * Organization: https://github.com/OpenRTL-Lab
 * Website: https://openrtl.top/
 *
 * Project: Goldilocks NTT/INTT Hardware Accelerator
 * Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
 * Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
 * File: operators/goldilocks-ntt/cmodel/asic_ntt_reference.c
 *
 * SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
 * Copyright applies to original project contributions only.
 * SPDX-License-Identifier: MIT
 * See LICENSE and applicable third-party notices.
 * OpenRTL-Header-End
 */

#define _POSIX_C_SOURCE 200809L
/* Software-only Goldilocks oracle. No DUT access, timing or handshakes.
 *
 * Usage: asic_ntt_reference OUTDIR LOGN (4..16). OUTDIR must not exist.
 * Four files are individually atomically published and fsynced; stdout's one
 * JSON line is the success marker for the caller's persistent manifest. A
 * failure preserves the fresh directory, including any partial .tmp evidence;
 * never reuse it. Coefficient 2*i is LOW64 and 2*i+1 is HIGH64 on each hex128
 * line. Frames are contiguous, natural order, with forward then inverse for
 * zero, ones, boundaries, random, random_repeat. Inverse inputs are original
 * patterns, NOT forward outputs. Both random patterns are byte-identical.
 *
 * Exact root constants: src/main/scala/ntt/GoldilocksReference.scala.
 * All division/remainder below is software arithmetic, not synthesizable RTL.
 */
#include <errno.h>
#include <fcntl.h>
#include <inttypes.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

typedef __uint128_t u128;
static const uint64_t P = UINT64_C(18446744069414584321);
static const uint64_t SEED = UINT64_C(0x6a09e667f3bcc909);
static const uint64_t ROOTS[17] = {
    UINT64_C(1), UINT64_C(18446744069414584320),
    UINT64_C(281474976710656), UINT64_C(16777216), UINT64_C(4096),
    UINT64_C(64), UINT64_C(8), UINT64_C(2198989700608),
    UINT64_C(4404853092538523347), UINT64_C(6434636298004421797),
    UINT64_C(4255134452441852017), UINT64_C(9113133275150391358),
    UINT64_C(4355325209153869931), UINT64_C(4308460244895131701),
    UINT64_C(7126024226993609386), UINT64_C(1873558160482552414),
    UINT64_C(8167150655112846419)
};
static const uint64_t BOUNDARIES[16] = {
    UINT64_C(0), UINT64_C(1), UINT64_C(18446744069414584320),
    UINT64_C(18446744069414584319), UINT64_C(0xffffffff),
    UINT64_C(0x100000000), UINT64_C(0x8000000000000000),
    UINT64_C(0x0123456789abcdef), UINT64_C(0xfedcba9876543210),
    UINT64_C(0xdeadbeefcafebabe), UINT64_C(0x1111222233334444),
    UINT64_C(0x5555666677778888), UINT64_C(0x9999aaaabbbbcccc),
    UINT64_C(0xaaaaaaaaaaaaaaaa), UINT64_C(0x5555555555555555),
    UINT64_C(0x7fffffffffffffff)
};
static const char *const PATTERNS[5] = {
    "zero", "ones", "boundaries", "random", "random_repeat"
};

static void fail(const char *message)
{
    fprintf(stderr, "asic_ntt_reference: %s\n", message);
    exit(EXIT_FAILURE);
}

static void fail_errno(const char *operation)
{
    fprintf(stderr, "asic_ntt_reference: %s: %s\n", operation, strerror(errno));
    exit(EXIT_FAILURE);
}

static void require(bool condition, const char *message)
{
    if (!condition)
        fail(message);
}

/* Canonical operands are required. 128 bits cover even the full 64x64
 * product without overflow. Cast BEFORE addition/multiplication. */
static uint64_t add_mod(uint64_t a, uint64_t b)
{
    return (uint64_t)(((u128)a + b) % P);
}

static uint64_t sub_mod(uint64_t a, uint64_t b)
{
    return (uint64_t)(((u128)a + P - b) % P);
}

static uint64_t mul_mod(uint64_t a, uint64_t b)
{
    return (uint64_t)(((u128)a * b) % P);
}

static uint64_t pow_mod(uint64_t base, uint64_t exponent)
{
    uint64_t result = 1;
    while (exponent != 0) {
        if ((exponent & 1) != 0)
            result = mul_mod(result, base);
        base = mul_mod(base, base);
        exponent >>= 1;
    }
    return result;
}

static uint64_t inverse_mod(uint64_t a)
{
    require(a != 0 && a < P, "inverse operand is not a nonzero field element");
    return pow_mod(a, P - 2);
}

/* SplitMix64, wrapping UINT64 arithmetic, then software canonicalization. */
static uint64_t random_word(uint64_t *state)
{
    uint64_t z = (*state += UINT64_C(0x9e3779b97f4a7c15));
    z = (z ^ (z >> 30)) * UINT64_C(0xbf58476d1ce4e5b9);
    z = (z ^ (z >> 27)) * UINT64_C(0x94d049bb133111eb);
    return (z ^ (z >> 31)) % P;
}

static void fill_pattern(uint64_t *data, size_t n, unsigned pattern)
{
    uint64_t state = SEED;
    for (size_t i = 0; i < n; ++i) {
        switch (pattern) {
        case 0: data[i] = 0; break;
        case 1: data[i] = 1; break;
        case 2: data[i] = BOUNDARIES[i % 16]; break;
        default: data[i] = random_word(&state); break;
        }
    }
}

/* Independent bit-reversed, in-place radix-2 DIT. Unlike the DUT's Stockham
 * schedule and inverse half-stages, this normalizes only once at the end. */
static void transform(uint64_t *data, unsigned logn, bool inverse)
{
    const size_t n = (size_t)1 << logn;
    const uint64_t root = inverse ? inverse_mod(ROOTS[logn]) : ROOTS[logn];
    for (size_t i = 0; i < n; ++i) {
        size_t source = i, reversed = 0;
        for (unsigned bit = 0; bit < logn; ++bit) {
            reversed = (reversed << 1) | (source & 1);
            source >>= 1;
        }
        if (reversed > i) {
            const uint64_t temporary = data[i];
            data[i] = data[reversed];
            data[reversed] = temporary;
        }
    }
    for (size_t length = 2; length <= n; length <<= 1) {
        const uint64_t stage_root = pow_mod(root, (uint64_t)(n / length));
        for (size_t base = 0; base < n; base += length) {
            uint64_t twiddle = 1;
            for (size_t offset = 0; offset < length / 2; ++offset) {
                const uint64_t a = data[base + offset];
                const uint64_t b = mul_mod(data[base + offset + length / 2], twiddle);
                data[base + offset] = add_mod(a, b);
                data[base + offset + length / 2] = sub_mod(a, b);
                twiddle = mul_mod(twiddle, stage_root);
            }
        }
    }
    if (inverse) {
        const uint64_t scale = inverse_mod((uint64_t)n);
        for (size_t i = 0; i < n; ++i)
            data[i] = mul_mod(data[i], scale);
    }
}

/* Direct definition, no butterflies/bit reversal or stage-root recurrence. */
static void direct_dft(const uint64_t *input, uint64_t *output,
                       unsigned logn, bool inverse)
{
    const size_t n = (size_t)1 << logn;
    const uint64_t root = inverse ? inverse_mod(ROOTS[logn]) : ROOTS[logn];
    const uint64_t scale = inverse ? inverse_mod((uint64_t)n) : 1;
    for (size_t k = 0; k < n; ++k) {
        uint64_t sum = 0;
        for (size_t j = 0; j < n; ++j)
            sum = add_mod(sum, mul_mod(input[j], pow_mod(root, (uint64_t)(j * k))));
        output[k] = mul_mod(sum, scale);
    }
}

/* Audit-only 64-bit compare/subtract arithmetic. In the no-reduction branch,
 * a+b is strictly below p, so it cannot wrap. No 128-bit remainder is used. */
static uint64_t audit_add(uint64_t a, uint64_t b)
{
    return a >= P - b ? a - (P - b) : a + b;
}

static uint64_t audit_multiply(uint64_t a, uint64_t b)
{
    uint64_t result = 0;
    while (b != 0) {
        if ((b & 1) != 0)
            result = audit_add(result, a);
        a = audit_add(a, a);
        b >>= 1;
    }
    return result;
}

/* Boundaries span zero/one, p neighbors, every power-of-two transition,
 * limb carries and canonical alternating bit patterns. Independent 64-bit
 * shift/add multiplication and borrow subtraction are arithmetic oracles. */
static void audit_arithmetic(void)
{
    uint64_t values[256];
    size_t count = 0;
    for (size_t i = 0; i < 16; ++i)
        values[count++] = BOUNDARIES[i];
    for (unsigned bit = 0; bit < 64; ++bit) {
        const uint64_t power = UINT64_C(1) << bit;
        values[count++] = power - 1;
        values[count++] = power;
        values[count++] = power + 1;
    }
    require((uint64_t)((u128)UINT64_MAX % P) == UINT64_C(0xfffffffe),
            "full-word canonical reduction failed");
    require((uint64_t)((u128)P % P) == 0 &&
            (uint64_t)(((u128)P + 1) % P) == 1,
            "modulus-neighbor canonical reduction failed");
    require(mul_mod(P - 1, P - 1) == 1, "max-canonical product failed");
    require(mul_mod(UINT64_C(0x100000000), UINT64_C(0x100000000)) ==
            UINT64_C(0xffffffff), "2^64 reduction failed");
    require(mul_mod(P - 2, P - 2) == 4, "p-2 product failed");
    require(pow_mod(0, 0) == 1 && pow_mod(0, 1) == 0, "zero power failed");
    for (size_t i = 0; i < count; ++i) {
        const uint64_t a = values[i];
        require(a < P, "noncanonical arithmetic audit operand");
        require(mul_mod(a, 0) == 0 && mul_mod(a, 1) == a, "multiply identities failed");
        if (a != 0)
            require(mul_mod(a, inverse_mod(a)) == 1, "multiplicative inverse failed");
        for (size_t j = 0; j < count; ++j) {
            const uint64_t b = values[j];
            const uint64_t sum = audit_add(a, b);
            const uint64_t difference = a >= b ? a - b : P - (b - a);
            const uint64_t product = mul_mod(a, b);
            require(add_mod(a, b) == sum, "boundary addition failed");
            require(sub_mod(a, b) == difference, "boundary subtraction failed");
            require(product < P && product == mul_mod(b, a), "boundary product failed");
            require(product == audit_multiply(a, b), "independent boundary product failed");
            require(add_mod(product, mul_mod(a, sub_mod(0, b))) == 0,
                    "boundary product negation failed");
            /* Doubling independently validates carry handling in products. */
            require(mul_mod(a, add_mod(b, b)) == add_mod(product, product),
                    "boundary product distributivity failed");
        }
    }
    for (unsigned logn = 0; logn <= 16; ++logn) {
        const uint64_t n = UINT64_C(1) << logn;
        require(ROOTS[logn] < P && pow_mod(ROOTS[logn], n) == 1,
                "root does not have dividing order N");
        if (logn != 0) {
            require(pow_mod(ROOTS[logn], n / 2) == P - 1,
                    "root does not have exact order N");
            require(mul_mod(ROOTS[logn], ROOTS[logn]) == ROOTS[logn - 1],
                    "root table is not coherent");
        }
        require(mul_mod(ROOTS[logn], inverse_mod(ROOTS[logn])) == 1,
                "inverse root failed");
    }
}

static void audit_transform(const uint64_t *input, const uint64_t *expected,
                            uint64_t *scratch, unsigned logn, bool inverse)
{
    const size_t n = (size_t)1 << logn;
    if (n <= 64) {
        direct_dft(input, scratch, logn, inverse);
        require(memcmp(expected, scratch, n * sizeof(*scratch)) == 0,
                "iterative/direct DFT disagreement");
    }
    memcpy(scratch, expected, n * sizeof(*scratch));
    transform(scratch, logn, !inverse);
    require(memcmp(input, scratch, n * sizeof(*scratch)) == 0, "roundtrip disagreement");
    for (size_t i = 0; i < n; ++i)
        require(expected[i] < P, "noncanonical transform output");
}

static FILE *open_output(int directory, const char *name)
{
    const int fd = openat(directory, name, O_WRONLY | O_CREAT | O_EXCL, 0600);
    if (fd < 0)
        fail_errno("create fresh vector file");
    FILE *file = fdopen(fd, "w");
    if (file == NULL) {
        const int saved = errno;
        (void)close(fd);
        errno = saved;
        fail_errno("open vector stream");
    }
    return file;
}

static void publish_output(FILE *file, int directory, const char *temporary,
                           const char *name)
{
    if (fflush(file) != 0 || fsync(fileno(file)) != 0)
        fail_errno("persist vector contents");
    if (fclose(file) != 0)
        fail_errno("close vector stream");
    /* Atomic no-clobber publication on the same filesystem. This avoids
     * rename()'s overwrite behavior even if a final name appears unexpectedly. */
    if (linkat(directory, temporary, directory, name, 0) != 0)
        fail_errno("publish vector without overwriting");
    if (unlinkat(directory, temporary, 0) != 0)
        fail_errno("remove published temporary link");
}

static void write_frame(FILE *file, const uint64_t *data, size_t n)
{
    for (size_t i = 0; i < n; i += 2)
        if (fprintf(file, "%016" PRIx64 "%016" PRIx64 "\n", data[i + 1], data[i]) < 0)
            fail_errno("write vector frame");
}

static void write_twiddles(FILE *file, uint64_t root, size_t n)
{
    uint64_t twiddle = 1;
    for (size_t exponent = 0; exponent < n / 2; ++exponent) {
        if (fprintf(file, "%016" PRIx64 "\n", twiddle) < 0)
            fail_errno("write twiddle");
        require(twiddle == pow_mod(root, (uint64_t)exponent), "twiddle audit failed");
        twiddle = mul_mod(twiddle, root);
    }
}

static void metadata(unsigned logn)
{
    const size_t n = (size_t)1 << logn;
    printf("{\"schema\":\"asic-ntt-reference-v1\",\"logN\":%u,\"n\":%zu,"
           "\"frames\":10,\"coefficients_per_line\":2,\"lines_per_frame\":%zu,"
           "\"packing\":\"high64=coefficient[2i+1],low64=coefficient[2i]\","
           "\"modulus_hex\":\"%016" PRIx64 "\",\"root_hex\":\"%016" PRIx64 "\","
           "\"prng\":\"splitmix64\",\"seed_hex\":\"%016" PRIx64 "\","
           "\"arithmetic_audit\":true,\"root_order_audit\":true,\"roundtrip_audit\":true,"
           "\"direct_dft_audit\":%s,\"cases\":[",
           logn, n, n / 2, P, ROOTS[logn], SEED, n <= 64 ? "true" : "false");
    for (unsigned frame = 0; frame < 10; ++frame)
        printf("%s{\"frame\":%u,\"pattern\":\"%s\",\"direction\":\"%s\","
               "\"inverse\":%s,\"line_offset\":%zu,\"line_count\":%zu}",
               frame == 0 ? "" : ",", frame, PATTERNS[frame / 2],
               (frame & 1) != 0 ? "inverse" : "forward",
               (frame & 1) != 0 ? "true" : "false", (size_t)frame * (n / 2), n / 2);
    printf("]}\n");
    if (fflush(stdout) != 0 || ferror(stdout))
        fail_errno("write metadata success line");
}

int main(int argc, char **argv)
{
    unsigned logn = 0;
    if (argc != 3 || argv[1][0] == '\0')
        fail("usage: asic_ntt_reference OUTDIR LOGN (4..16); OUTDIR must be fresh");
    for (unsigned candidate = 4; candidate <= 16; ++candidate) {
        char number[4];
        (void)snprintf(number, sizeof(number), "%u", candidate);
        if (strcmp(argv[2], number) == 0)
            logn = candidate;
    }
    require(logn != 0, "LOGN must be a decimal integer 4..16 (no signs or padding)");
    audit_arithmetic();
    const size_t n = (size_t)1 << logn;
    uint64_t *input = malloc(n * sizeof(*input));
    uint64_t *expected = malloc(n * sizeof(*expected));
    uint64_t *scratch = malloc(n * sizeof(*scratch));
    require(input != NULL && expected != NULL && scratch != NULL, "allocation failed");
    if (mkdir(argv[1], 0700) != 0)
        fail_errno("mkdir fresh OUTDIR (existing paths are forbidden)");
    const int directory = open(argv[1], O_RDONLY | O_DIRECTORY | O_NOFOLLOW);
    if (directory < 0)
        fail_errno("open fresh OUTDIR");
    FILE *inputs = open_output(directory, "input.hex.tmp");
    FILE *outputs = open_output(directory, "expected.hex.tmp");
    FILE *forward = open_output(directory, "forward-twiddle.hex.tmp");
    FILE *inverse = open_output(directory, "inverse-twiddle.hex.tmp");
    write_twiddles(forward, ROOTS[logn], n);
    write_twiddles(inverse, inverse_mod(ROOTS[logn]), n);
    for (unsigned pattern = 0; pattern < 5; ++pattern) {
        fill_pattern(input, n, pattern);
        for (unsigned direction = 0; direction < 2; ++direction) {
            memcpy(expected, input, n * sizeof(*expected));
            transform(expected, logn, direction != 0);
            audit_transform(input, expected, scratch, logn, direction != 0);
            write_frame(inputs, input, n);
            write_frame(outputs, expected, n);
        }
    }
    publish_output(inputs, directory, "input.hex.tmp", "input.hex");
    publish_output(outputs, directory, "expected.hex.tmp", "expected.hex");
    publish_output(forward, directory, "forward-twiddle.hex.tmp", "forward-twiddle.hex");
    publish_output(inverse, directory, "inverse-twiddle.hex.tmp", "inverse-twiddle.hex");
    if (fsync(directory) != 0)
        fail_errno("persist OUTDIR entries");
    if (close(directory) != 0)
        fail_errno("close OUTDIR");
    /* Also persist the newly created directory's entry in its parent. */
    char *parent = strdup(argv[1]);
    require(parent != NULL, "parent path allocation failed");
    size_t length = strlen(parent);
    while (length > 1 && parent[length - 1] == '/')
        parent[--length] = '\0';
    char *slash = strrchr(parent, '/');
    if (slash == NULL) {
        free(parent);
        parent = strdup(".");
        require(parent != NULL, "parent path allocation failed");
    } else if (slash == parent) {
        slash[1] = '\0';
    } else {
        *slash = '\0';
    }
    const int parent_fd = open(parent, O_RDONLY | O_DIRECTORY);
    if (parent_fd < 0 || fsync(parent_fd) != 0)
        fail_errno("persist parent directory");
    if (close(parent_fd) != 0)
        fail_errno("close parent directory");
    free(parent);
    free(scratch);
    free(expected);
    free(input);
    metadata(logn);
    return EXIT_SUCCESS;
}
