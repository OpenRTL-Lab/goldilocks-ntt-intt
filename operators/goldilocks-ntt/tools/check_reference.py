#!/usr/bin/env python3
# OpenRTL-Header-Begin
# OpenRTL — Open-source hardware accelerators.
# Organization: https://github.com/OpenRTL-Lab
# Website: https://openrtl.top/
#
# Project: Goldilocks NTT/INTT Hardware Accelerator
# Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
# Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
# File: operators/goldilocks-ntt/tools/check_reference.py
#
# SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
# Copyright applies to original project contributions only.
# SPDX-License-Identifier: MIT
# See LICENSE and applicable third-party notices.
# OpenRTL-Header-End

"""Independent Python big-integer reference: every input, output and twiddle."""
import json
from pathlib import Path
import sys

P = (1 << 64) - (1 << 32) + 1
MASK = (1 << 64) - 1
ROOTS = (1,18446744069414584320,281474976710656,16777216,4096,64,8,
    2198989700608,4404853092538523347,6434636298004421797,4255134452441852017,
    9113133275150391358,4355325209153869931,4308460244895131701,
    7126024226993609386,1873558160482552414,8167150655112846419)
BOUNDARIES = (0,1,P-1,P-2,(1<<32)-1,1<<32,1<<63,0x0123456789abcdef,
    0xfedcba9876543210,0xdeadbeefcafebabe,0x1111222233334444,0x5555666677778888,
    0x9999aaaabbbbcccc,0xaaaaaaaaaaaaaaaa,0x5555555555555555,(1<<63)-1)


def inputs(n):
    state = 0x6a09e667f3bcc909
    random = []
    for _ in range(n):
        state = (state + 0x9e3779b97f4a7c15) & MASK
        z = ((state ^ (state >> 30)) * 0xbf58476d1ce4e5b9) & MASK
        z = ((z ^ (z >> 27)) * 0x94d049bb133111eb) & MASK
        random.append((z ^ (z >> 31)) % P)
    return [[0]*n,[1]*n,[BOUNDARIES[i%16] for i in range(n)],random,random.copy()]


def transform(data, logn, inverse):
    n = 1 << logn
    a = [data[int(f'{i:0{logn}b}'[::-1], 2)] for i in range(n)]
    root = pow(ROOTS[logn], P-2, P) if inverse else ROOTS[logn]
    half = pow(2, P-2, P)
    for stage in range(1, logn+1):
        length = 1 << stage
        twiddles = [pow(root, j*(n//length), P) for j in range(length//2)]
        for base in range(0, n, length):
            for j,w in enumerate(twiddles):
                u,v = a[base+j], a[base+j+length//2]*w%P
                a[base+j], a[base+j+length//2] = (u+v)%P,(u-v)%P
                if inverse:
                    a[base+j] = a[base+j]*half%P
                    a[base+j+length//2] = a[base+j+length//2]*half%P
    return a


def packed(path):
    return [v for line in path.read_text().splitlines()
            for v in (int(line,16)&MASK,int(line,16)>>64)]


def check(directory, logn):
    n = 1 << logn
    source, expected = packed(directory/'input.hex'),packed(directory/'expected.hex')
    assert len(source)==len(expected)==10*n
    for log in range(logn+1):
        assert pow(ROOTS[log],1<<log,P)==1
        if log:
            assert pow(ROOTS[log],1<<(log-1),P)==P-1
            assert pow(ROOTS[log],2,P)==ROOTS[log-1]
    for frame in range(10):
        data = inputs(n)[frame//2]
        assert source[frame*n:(frame+1)*n]==data
        assert expected[frame*n:(frame+1)*n]==transform(data,logn,bool(frame&1))
    for inverse,name in ((False,'forward-twiddle.hex'),(True,'inverse-twiddle.hex')):
        root = pow(ROOTS[logn],P-2,P) if inverse else ROOTS[logn]
        assert [int(x,16) for x in (directory/name).read_text().splitlines()]==[
            pow(root,i,P) for i in range(n//2)]
    print(json.dumps({'pass': True,'logN': logn,'frames': 10,'coefficients': 10*n}))


if __name__=='__main__':
    check(Path(sys.argv[1]),int(sys.argv[2]))
