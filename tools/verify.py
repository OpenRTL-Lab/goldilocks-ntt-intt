#!/usr/bin/env python3
# OpenRTL-Header-Begin
# OpenRTL — Open-source hardware accelerators.
# Organization: https://github.com/OpenRTL-Lab
# Website: https://openrtl.top/
#
# Project: Goldilocks NTT/INTT Hardware Accelerator
# Chisel NTT and inverse NTT over the Goldilocks field, with generated SystemVerilog.
# Repository: https://github.com/OpenRTL-Lab/goldilocks-ntt-intt
# File: tools/verify.py
#
# SPDX-FileCopyrightText: 2026 Goldilocks NTT/INTT contributors
# Copyright applies to original project contributions only.
# SPDX-License-Identifier: MIT
# See LICENSE and applicable third-party notices.
# OpenRTL-Header-End

"""Regenerate, compare C/Python/RTL, replay bundled SV and measure native throughput."""
import argparse
import json
import os
from pathlib import Path
import re
import sys

from emit_sv import ROOT, OP, TOP, BUNDLED, code, filelist, generate, product_closure, run


def rows(path, prefix):
    return [dict((key,int(value)) for key,value in re.findall(r'(\w+)=(\d+)',line))
            for line in re.findall('^'+prefix+r' (.*)$',path.read_text(),re.M)]


def compile_binary(sources, top, driver, out, label, jobs):
    obj = out/(label+'-obj')
    obj.mkdir()
    listing = out/(label+'.f')
    filelist(sources,listing)
    run(['verilator','-O3','--assert','--cc','--exe','--build','-j',jobs,
        '-DENABLE_INITIAL_MEM_','--top-module',top,'-Mdir',obj,
        '-CFLAGS','-O2 -std=c++17','-f',listing,ROOT/OP/'sim'/driver],out,label+'-compile')
    return ROOT/obj/('V'+top)


def env_test(rtl, out, label, jobs, expected=0, bundled=False):
    sources = list(rtl.glob('*.sv'))
    if bundled:
        product = set(product_closure(rtl))
        sources = [p for p in sources if p not in product] + list(BUNDLED.glob('*.sv'))
    exe = compile_binary(sources,'AsicNttPairSimTop','asic_ntt_pair_sim.cpp',out,label,jobs)
    run([exe],out,label,expected)
    result = rows(out/(label+'.log'),'RESULT')[0]
    assert result['done']==1 and result['timedOut']==0 and result['frames']==10
    assert result['pass']==(0 if expected else 1)
    assert result['mismatches']==(1 if expected else 0)
    return {'result':result,'frames':rows(out/(label+'.log'),'FRAME')}


def measure(rtl, out, logn, jobs, tasks, bundled=False):
    label='bundled-native' if bundled else 'generated-native'
    sources=list(BUNDLED.glob('*.sv')) if bundled else product_closure(rtl)
    exe=compile_binary(sources,TOP,'native_performance.cpp',out,label,jobs)
    values={}
    for isolated in (0,1):
        name=label+('-isolated' if isolated else '-saturated')
        run([exe,ROOT/rtl.parent/'vectors',logn,tasks,isolated],out,name)
        assert rows(out/(name+'.log'),'RESULT')[0]['pass']==1
        accepts=rows(out/(name+'.log'),'ACCEPT')
        completes=rows(out/(name+'.log'),'COMPLETE')
        assert len(accepts)==len(completes)==tasks
        assert [r['tag'] for r in completes]==list(range(tasks))
        assert all(r['errors']==0 for r in completes)
        values['isolated' if isolated else 'saturated']=completes
    c=values['saturated']; a,b=c[2],c[-3]
    duration,intervals=b['cycle']-a['cycle'],b['tag']-a['tag']
    return {'tasks':tasks,'checked_coefficients_per_run':tasks*(1<<logn),
        'isolated_latency_cycles':sorted({r['latency'] for r in values['isolated']}),
        'saturated_latency_cycles':sorted({r['latency'] for r in c}),
        'frame_latency_cycles':sorted({r['frame'] for r in c}),
        'compute_cycles':sorted({r['compute'] for r in c}),
        'steady_first_tag':a['tag'],'steady_last_tag':b['tag'],
        'steady_cycles':duration,'steady_intervals':intervals,
        'steady_cycles_per_task':duration/intervals,'tasks_per_cycle':intervals/duration}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--out',default='build/verify')
    parser.add_argument('--logn',type=int,nargs='+',default=[4,6,10,14,16])
    parser.add_argument('--jobs',type=int,default=2)
    parser.add_argument('--tasks',type=int,default=32)
    args=parser.parse_args()
    assert 1<=args.jobs<=64 and 8<=args.tasks<=128
    assert len(set(args.logn))==len(args.logn) and all(4<=x<=16 for x in args.logn)
    os.chdir(ROOT);os.environ['CCACHE_DISABLE']='1'
    from emit_sv import fresh
    base=fresh(args.out)
    report={'version':'1.1.0','ideal_interfaces':True,'dc_run':False,'cases':[]}
    for logn in args.logn:
        rtl=generate(str(base/f'logn{logn}'),logn,'ideal');out=rtl.parent
        run(['verilator','--lint-only','--assert','--top-module',TOP,'-f',rtl/'product.f'],out,'lint')
        case={'logN':logn,'n':1<<logn,'generated':env_test(rtl,out,'generated',args.jobs)}
        case['performance']=measure(rtl,out,logn,args.jobs,args.tasks)
        if logn==16:
            originals={p.name:p for p in BUNDLED.glob('*.sv')}
            newly={p.name:p for p in product_closure(rtl)}
            assert originals.keys()==newly.keys(),'Product module set differs'
            for name in originals:
                assert re.sub(r'\s+','',code(originals[name].read_text()))==re.sub(
                    r'\s+','',code(newly[name].read_text())),'Product differs: '+name
            case['bundled']=env_test(rtl,out,'bundled',args.jobs,bundled=True)
            assert case['bundled']==case['generated']
            case['bundled_performance']=measure(rtl,out,logn,args.jobs,args.tasks,True)
            assert case['bundled_performance']==case['performance']
            case['bundled_matches_generated']=True
        report['cases'].append(case)
    for mode in ('protocol','reset'):
        rtl=generate(str(base/mode),4,mode)
        report[mode]=env_test(rtl,rtl.parent,mode,args.jobs)
    rtl=generate(str(base/'negative'),4,'ideal')
    expected=rtl.parent/'vectors/expected.hex'
    lines=expected.read_text().splitlines()
    lines[0]=f'{int(lines[0],16)^1:032x}'
    expected.write_text('\n'.join(lines)+'\n')
    report['negative']=env_test(rtl,rtl.parent,'negative',args.jobs,1)
    report['status']=0
    (base/'results.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2))


if __name__=='__main__':
    main()
