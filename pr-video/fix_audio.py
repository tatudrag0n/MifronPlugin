#!/usr/bin/env python3
"""Re-mix narration + BGM onto the already-rendered mifron-pr.mp4 video.

The first render used ffmpeg amix with default normalization, which divided
the mix by the input count and left narration too quiet. This script keeps
the video stream untouched (-c:v copy) and rebuilds only the audio track
with amix normalize=0, matching the fixed filter in render.py.
"""
import os
import subprocess
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from render import FFMPEG, NARRATION, HEAD_PAD, TAIL_PAD, probe_duration, synth_bgm

ROOT = os.path.dirname(os.path.abspath(__file__))

def main():
    durations = [probe_duration(p) for p in NARRATION]
    scene_durs = []
    for i, d in enumerate(durations):
        tail = 2.4 if i == len(durations) - 1 else TAIL_PAD
        scene_durs.append(HEAD_PAD + d + tail)
    total = sum(scene_durs)
    starts, acc = [], 0.0
    for d in scene_durs:
        starts.append(acc)
        acc += d
    print(f"total={total:.2f}s starts={[round(s,1) for s in starts]}", flush=True)

    src = os.path.join(ROOT, "mifron-pr.mp4")
    tmp = os.path.join(ROOT, "_fixed.mp4")
    bgm = os.path.join(ROOT, "_bgm.wav")
    synth_bgm(bgm, total)

    inputs = ["-i", src, "-i", bgm]
    for p in NARRATION:
        inputs += ["-i", p]
    fc = ["[1:a]volume=0.30[bgm]"]
    labels = ["[bgm]"]
    for i, st in enumerate(starts):
        delay = int((st + HEAD_PAD) * 1000)
        fc.append(f"[{i+2}:a]adelay={delay}|{delay},volume=1.0[n{i}]")
        labels.append(f"[n{i}]")
    fc.append("".join(labels) + f"amix=inputs={len(labels)}:duration=first:dropout_transition=0:normalize=0,"
              "volume=2.2,alimiter=limit=0.94[aout]")
    cmd = [FFMPEG, "-y"] + inputs + [
        "-filter_complex", ";".join(fc),
        "-map", "0:v", "-map", "[aout]",
        "-c:v", "copy", "-c:a", "aac", "-b:a", "192k",
        "-t", f"{total:.3f}", "-movflags", "+faststart", tmp]
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stderr[-3000:])
        raise RuntimeError("remix failed")
    os.replace(tmp, src)
    os.remove(bgm)
    print("DONE remix", flush=True)

if __name__ == "__main__":
    main()
