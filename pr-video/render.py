#!/usr/bin/env python3
"""Mifron PR video renderer.

Spec-grounded PR video for the Mifron Paper plugin.
Sources: README.md, docs/SPECIFICATION.md, docs/non-ffa-code-spec.md

Pipeline:
  1. Probe narration mp3 durations (ffmpeg -i)
  2. Render animated frames with Pillow, pipe rawvideo into ffmpeg (silent mp4)
  3. Synthesize an original chiptune-style BGM (numpy -> wav)
  4. Mix narration (timed per scene) + BGM, encode final mp4
  5. Export thumbnail.png
"""
import glob
import math
import os
import re
import subprocess
import sys
import wave

import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ROOT = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(ROOT, "assets")
AUDIO = os.path.join(ROOT, "audio")
W, H, FPS = 1920, 1080, 30

# ---------------------------------------------------------------- fonts
def find_font():
    candidates = [
        os.path.join(ROOT, "fonts", "ipaexg.ttf"),
    ] + glob.glob(os.path.join(ROOT, ".venv", "lib", "python*", "site-packages",
                               "japanize_matplotlib", "fonts", "ipaexg.ttf"))
    for c in candidates:
        if os.path.exists(c):
            return c
    raise FileNotFoundError("ipaexg.ttf not found")

FONT_PATH = find_font()
_font_cache = {}

def font(size):
    if size not in _font_cache:
        _font_cache[size] = ImageFont.truetype(FONT_PATH, size)
    return _font_cache[size]

def ffmpeg_exe():
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        return "ffmpeg"

FFMPEG = ffmpeg_exe()

def probe_duration(path):
    out = subprocess.run([FFMPEG, "-i", path], capture_output=True, text=True)
    m = re.search(r"Duration:\s*(\d+):(\d+):([\d.]+)", out.stderr)
    if not m:
        raise RuntimeError(f"cannot probe {path}: {out.stderr[-500:]}")
    return int(m.group(1)) * 3600 + int(m.group(2)) * 60 + float(m.group(3))

def clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))

def ease_out(t):
    return 1 - (1 - clamp(t)) ** 3

# ---------------------------------------------------------------- scenes
# bullets/chips/captions are verbatim-grounded in the spec docs (see STORYBOARD.md)
SCENES = [
    dict(
        id="01-opening", bg="hero.jpg", layout="opening",
        kicker="PAPER 26.1.2 向け統合プラグイン",
        title="MIFRON",
        subtitle="経済で広がる、Minecraftサバイバル",
        bullets=["通貨MPを軸にした経済", "SHOP・クエスト・レリックが連動", "新規参加から転生まで、一貫した導線"],
        caption=["ようこそ、Mifronへ。Paper 26.1.2向け統合プラグインが、",
                 "Minecraftのサバイバルを、経済で広がる冒険へと変えます。"],
        tint=(10, 30, 60, 70),
    ),
    dict(
        id="02-tutorial", bg="hero.jpg", layout="standard", pan=0.55,
        kicker="01 ─ はじめての導線",
        title="7段階チュートリアル",
        subtitle="迷わず、卒業まで",
        bullets=[
            "メニュー → 討伐 → Elite → レリック → クエスト／目標 → 金床 → 卒業",
            "途中離脱しても進捗から再開（data.ymlに保存）",
            "卒業でガイド本「Mifron はじめの一歩」を配布",
            "目標チェックリスト5項目・各300MP",
        ],
        caption=["初参加は7段階のチュートリアルから。卒業すれば、遊び方ガイド",
                 "「はじめの一歩」と、5つの目標があなたを後押しします。"],
        tint=(20, 40, 20, 55),
    ),
    dict(
        id="03-mp-shop", bg="shop.jpg", layout="standard",
        kicker="02 ─ 通貨MPとSHOP",
        title="稼ぐ、使う、回す経済",
        subtitle="通貨はMP ─ エメラルドとは連動しません",
        bullets=[
            "SHOP 5カテゴリ：装飾／材料／道具類／食料／その他（1ページ40件）",
            "在庫で価格が変動（0.25〜4倍）／月初に各商品+5補充",
            "/mf pay <player> <amount> で送金",
            "死亡時は所持MPの50%を喪失 ─ 緊張感も設計のうち",
        ],
        caption=["通貨はMP。カテゴリ別SHOPは在庫で価格が変動し、",
                 "クエストや取引で稼いで、送金も可能。死亡時は半分を失います。"],
        tint=(60, 40, 5, 55),
    ),
    dict(
        id="04-shops", bg="shop.jpg", layout="standard", pan=0.6,
        kicker="03 ─ 棚・樽・商人",
        title="自分だけの商売をひらく",
        subtitle="棚ショップ／樽ショップ／通常商人・秘宝商人",
        bullets=[
            "棚ショップ：同じ素材の在庫は全棚で共有",
            "樽ショップ：空の樽＋棚で販売・買取（既定1,000MP）",
            "通常商人：購入・売却候補 各8件／1ページ6件表示",
            "秘宝商人：特殊品だけを分離販売 ─ 通常テーブルに混ざらない",
        ],
        caption=["棚ショップは全棚で在庫を共有。樽ショップや通常商人、",
                 "秘宝商人が、あなただけの商売と出会いを生みます。"],
        tint=(5, 45, 45, 55),
    ),
    dict(
        id="05-menu", bg="hero.jpg", layout="chips", pan=0.3,
        kicker="04 ─ 扇状メニュー",
        title="配布は「メニュー」1個だけ",
        subtitle="右クリックで10項目が目の前に扇状に展開",
        bullets=[
            "左クリックで選択 ／ 20秒・離脱で自動消滅",
            "ただし棚ショップの右クリックは購入を優先",
        ],
        chips=["SHOP", "ウォレット", "ステータス", "クエスト", "取引",
               "投票", "ログボ", "目標", "ガイド", "チュートリアル"],
        caption=["配布はメニューひとつだけ。右クリックで10項目が扇状に展開し、",
                 "SHOPもウォレットもクエストも、迷わず選べます。"],
        tint=(30, 20, 60, 60),
    ),
    dict(
        id="06-relics", bg="relic.jpg", layout="chips",
        kicker="05 ─ レリック 全23種",
        title="素材から生まれる、特別な力",
        subtitle="確率 ＝ 素材の希少性 × レリックの強さ（売買不可）",
        bullets=[
            "Elite討伐ドロップ／チュートリアル配布でも入手",
            "エリートWarden討伐：残響の結晶が確定ドロップ",
            "例）朽ちた剣 0.320% ／ ジェットパック 0.040%",
        ],
        chips=["フックショット 100ブロック", "ジェットパック", "次元の眼 瞬間移動",
               "残響の結晶", "朽ちた剣", "バニーホップ", "全適用の魔導書",
               "巨人の心臓", "＋ ほか15種"],
        caption=["素材から低確率で生まれるレリックは全23種。フックショット、",
                 "ジェットパック、次元の眼。Warden討伐では残響の結晶が確定ドロップ。"],
        tint=(35, 5, 55, 65),
    ),
    dict(
        id="07-trade-quest", bg="shop.jpg", layout="standard", pan=0.15,
        kicker="06 ─ 安全取引と挑戦",
        title="詐欺を防ぐ取引、続く挑戦",
        subtitle="/trade ─ 提示枠16枠×2 ＋ 提示MP（上限100万MP）",
        bullets=[
            "提示を変えると双方の確定がリセット ─ 同時交換でのみ成立",
            "閉鎖・退出・タイムアウト時は提示を全額返却",
            "クエスト39件（デイリー／ウィークリー／マンスリー／スペシャル）",
            "称号21種以上 ／ 図鑑 ／ 全進捗達成後の転生",
        ],
        caption=["取引は専用UIで同時交換。提示を変えれば確定はリセット。",
                 "39のクエスト、21種以上の称号と図鑑が、挑戦を記録します。"],
        tint=(5, 35, 60, 55),
    ),
    dict(
        id="08-closing", bg="community.jpg", layout="closing",
        kicker="07 ─ 毎日が、イベントになる",
        title="さあ、はじめの一歩を",
        subtitle="ログボ ／ 紹介 ／ 定期イベント",
        bullets=[
            "ログインボーナス：連続7・30・100日などの節目で追加報酬＋全体告知",
            "紹介制度：卒業時に双方3,000MP（紹介者は週5件まで）",
            "定期イベント：毎週土曜20:00「釣り大会」（限定クエスト連動）",
        ],
        cta="/mf guide",
        caption=["ログインボーナス、紹介報酬、毎週土曜20時の釣り大会。",
                 "Mifronで、あなたの一歩がサーバーの物語になります。"],
        tint=(55, 30, 5, 60),
    ),
]

NARRATION = [os.path.join(AUDIO, f"narration-{i:02d}.mp3") for i in range(1, 9)]
HEAD_PAD, TAIL_PAD = 0.7, 0.8

# ---------------------------------------------------------------- BGM
def synth_bgm(path, total):
    """Original gentle chiptune loop: C - G - Am - F, pluck arpeggio + bass."""
    sr = 44100
    n = int(total * sr)
    t = np.arange(n) / sr
    out = np.zeros(n, dtype=np.float64)
    bpm = 100.0
    beat = 60.0 / bpm
    # chord roots (Hz): C3 G2 A2 F2 ; arpeggio notes from chord tones + octave
    chords = [
        (130.81, [261.63, 329.63, 392.00, 523.25]),   # C
        (98.00,  [246.94, 293.66, 392.00, 493.88]),   # G
        (110.00, [220.00, 261.63, 329.63, 440.00]),   # Am
        (87.31,  [174.61, 220.00, 261.63, 349.23]),   # F
    ]
    chord_len = beat * 4  # one bar per chord
    n_bars = int(math.ceil(total / chord_len))

    def pluck(freq, start, dur, amp):
        s = int(start * sr)
        e = min(n, int((start + dur) * sr))
        if s >= n or e <= s:
            return
        tt = np.arange(e - s) / sr
        env = np.exp(-3.5 * tt / dur) * np.minimum(1.0, tt / 0.01)
        wave_ = (np.sin(2 * np.pi * freq * tt)
                 + 0.5 * np.sin(2 * np.pi * freq * 2 * tt)
                 + 0.25 * np.sin(2 * np.pi * freq * 3 * tt))
        # soft square-ish chip flavour
        wave_ += 0.15 * np.sign(np.sin(2 * np.pi * freq * tt))
        out[s:e] += amp * env * wave_

    for bar in range(n_bars):
        root, notes = chords[bar % len(chords)]
        base = bar * chord_len
        # bass: half notes
        pluck(root, base, chord_len * 0.55, 0.20)
        pluck(root, base + chord_len * 0.5, chord_len * 0.55, 0.16)
        # arpeggio: 8th notes, up-down
        seq = [0, 1, 2, 3, 2, 1, 2, 3]
        for i, idx in enumerate(seq):
            pluck(notes[idx], base + i * (chord_len / 8), chord_len / 8 * 0.95, 0.11)
        # sparkle: bar-start high note every 2 bars
        if bar % 2 == 0:
            pluck(notes[3] * 2, base, beat * 0.8, 0.05)
    # global fade in/out
    fade = int(1.2 * sr)
    out[:fade] *= np.linspace(0, 1, fade)
    out[-fade:] *= np.linspace(1, 0, fade)
    out = np.clip(out, -1, 1)
    pcm = (out * 32767 * 0.9).astype(np.int16)
    with wave.open(path, "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sr)
        wf.writeframes(pcm.tobytes())

# ---------------------------------------------------------------- drawing
def load_bg(name, pan_seed=0.0):
    im = Image.open(os.path.join(ASSETS, name)).convert("RGB")
    # cover 2208x1242 canvas for Ken Burns crops
    scale = max(2208 / im.width, 1242 / im.height)
    im = im.resize((int(im.width * scale) + 2, int(im.height * scale) + 2),
                   Image.LANCZOS)
    x = max(0, (im.width - 2208) // 2)
    y = max(0, (im.height - 1242) // 2)
    return im.crop((x, y, x + 2208, y + 1242))

def make_overlays():
    # bottom gradient for readability
    grad = Image.new("L", (1, H), 0)
    for yy in range(H):
        if yy < int(H * 0.42):
            a = 40
        else:
            frac = max(0.0, (yy - H * 0.42) / (H * 0.58))
            a = int(40 + 185 * frac ** 1.4)
        grad.putpixel((0, yy), min(225, a))
    grad = grad.resize((W, H))
    dark = Image.new("RGBA", (W, H), (4, 8, 18, 255))
    dark.putalpha(grad)
    # top gradient
    grad2 = Image.new("L", (1, H), 0)
    for yy in range(H):
        grad2.putpixel((0, yy), int(120 * max(0.0, 1 - yy / (H * 0.30))))
    grad2 = grad2.resize((W, H))
    top = Image.new("RGBA", (W, H), (4, 8, 18, 255))
    top.putalpha(grad2)
    # vignette
    vig = Image.new("L", (W, H), 0)
    vd = ImageDraw.Draw(vig)
    vd.ellipse((-int(W * 0.25), -int(H * 0.25), int(W * 1.25), int(H * 1.25)), fill=255)
    vig = vig.filter(ImageFilter.GaussianBlur(180))
    black = Image.new("RGBA", (W, H), (0, 0, 0, 255))
    black.putalpha(Image.eval(vig, lambda p: 110 - int(p * 110 / 255)))
    capt = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    cd = ImageDraw.Draw(capt)
    cd.rectangle((0, H - 118, W, H), fill=(0, 0, 0, 168))
    cd.rectangle((0, H - 118, W, H - 114), fill=(84, 220, 140, 255))
    return dark, top, black, capt

OVERLAYS = make_overlays()

def text_w(draw, s, f):
    return draw.textbbox((0, 0), s, font=f)[2]

def draw_caption(base, lines):
    ov = OVERLAYS[3].copy()
    d = ImageDraw.Draw(ov)
    f = font(39)
    y = H - 104
    for line in lines:
        tw = text_w(d, line, f)
        d.text(((W - tw) / 2, y), line, font=f, fill=(255, 255, 255, 255),
               stroke_width=1, stroke_fill=(0, 0, 0, 255))
        y += 48
    base.alpha_composite(ov)

def rounded(d, box, r, **kw):
    d.rounded_rectangle(box, radius=r, **kw)

def render_frame(scene, bg, local, dur, global_t, total):
    # Ken Burns: slow zoom-in + pan
    p = clamp(local / max(dur, 0.01))
    pan0 = scene.get("pan", 0.0)
    view_w = 2140 - 190 * ease_out(p)
    view_h = view_w * 9 / 16
    max_x, max_y = 2208 - view_w, 1242 - view_h
    x = max_x * clamp(pan0 + 0.25 * p)
    y = max_y * (0.25 + 0.3 * p)
    frame = bg.crop((int(x), int(y), int(x + view_w), int(y + view_h))).resize((W, H), Image.BICUBIC).convert("RGBA")
    # tint
    tint = Image.new("RGBA", (W, H), scene["tint"])
    frame.alpha_composite(tint)
    for ov in OVERLAYS[:3]:
        frame.alpha_composite(ov)

    d = ImageDraw.Draw(frame, "RGBA")
    fade_in = clamp(local / 0.55)
    fade_out = clamp((dur - local) / 0.45)
    edge = min(fade_in, fade_out)

    # header: watermark + kicker chip
    d.text((W - 250, 34), "MIFRON", font=font(34), fill=(255, 255, 255, int(215 * edge)),
           stroke_width=2, stroke_fill=(0, 0, 0, int(160 * edge)))
    kicker = scene["kicker"]
    kf = font(30)
    kw = text_w(d, kicker, kf) + 44
    a = int(200 * clamp(local / 0.5))
    rounded(d, (92, 36, 92 + kw, 88), 12, fill=(8, 24, 18, a),
            outline=(84, 220, 140, a), width=2)
    d.text((114, 47), kicker, font=kf, fill=(170, 255, 205, a))

    layout = scene["layout"]
    dy = int((1 - ease_out(local / 0.7)) * 36)
    ta = int(255 * clamp(local / 0.6))

    if layout == "opening":
        tf = font(150)
        title = scene["title"]
        tw = text_w(d, title, tf)
        d.text(((W - tw) / 2, 268 + dy), title, font=tf,
               fill=(255, 255, 255, ta), stroke_width=4, stroke_fill=(10, 40, 22, ta))
        # underline
        uw = int(tw * 0.62 * ease_out(local / 0.9))
        d.rectangle(((W - uw) / 2, 452 + dy, (W + uw) / 2, 462 + dy),
                    fill=(84, 220, 140, ta))
        sf = font(52)
        sub = scene["subtitle"]
        sw = text_w(d, sub, sf)
        d.text(((W - sw) / 2, 492 + dy), sub, font=sf,
               fill=(220, 255, 232, ta), stroke_width=2, stroke_fill=(0, 0, 0, ta))
        # bullets as centered chips row (stacked)
        y = 610 + dy
        for i, b in enumerate(scene["bullets"]):
            ba = int(255 * clamp((local - 0.7 - i * 0.35) / 0.4))
            if ba <= 0:
                continue
            bf = font(36)
            bw = text_w(d, b, bf) + 56
            rounded(d, ((W - bw) / 2, y, (W + bw) / 2, y + 62), 31,
                    fill=(5, 18, 12, int(175 * ba / 255)), outline=(84, 220, 140, ba), width=2)
            d.text(((W - text_w(d, b, bf)) / 2, y + 12), b, font=bf,
                   fill=(255, 255, 255, ba))
            y += 80
    else:
        tf = font(76) if len(scene["title"]) <= 14 else font(64)
        d.text((108, 238 + dy), scene["title"], font=tf,
               fill=(255, 255, 255, ta), stroke_width=3, stroke_fill=(0, 0, 0, ta))
        sf = font(38)
        d.text((110, 238 + (104 if tf == font(76) else 94) + dy), scene["subtitle"],
               font=sf, fill=(178, 240, 200, ta), stroke_width=1, stroke_fill=(0, 0, 0, ta))
        top_y = 430 + dy
        if layout == "chips":
            chips = scene["chips"]
            cols = 3 if len(chips) > 6 else 5
            if len(chips) == 10:
                cols = 5
            chip_w, chip_h, gap = (W - 216 - (cols - 1) * 18) // cols, 74, 18
            cf = font(31)
            for i, c in enumerate(chips):
                ca = int(255 * clamp((local - 0.45 - i * 0.16) / 0.35))
                if ca <= 0:
                    continue
                cx = 108 + (i % cols) * (chip_w + gap)
                cy = top_y + (i // cols) * (chip_h + gap)
                rounded(d, (cx, cy, cx + chip_w, cy + chip_h), 14,
                        fill=(6, 20, 14, int(185 * ca / 255)),
                        outline=(84, 220, 140, ca), width=2)
                # shrink to fit
                f2 = cf
                while text_w(d, c, f2) > chip_w - 28 and f2.size > 20:
                    f2 = font(f2.size - 2)
                d.text((cx + (chip_w - text_w(d, c, f2)) / 2, cy + (chip_h - f2.size) / 2 - 2),
                       c, font=f2, fill=(255, 255, 255, ca))
            rows = math.ceil(len(chips) / cols)
            top_y += rows * (chip_h + gap) + 26
        # bullets
        bf = font(36)
        y = top_y
        for i, b in enumerate(scene["bullets"]):
            ba = int(255 * clamp((local - 0.55 - i * 0.5) / 0.4))
            if ba <= 0:
                continue
            # auto-shrink long bullets
            f2 = bf
            while text_w(d, b, f2) > W - 300 and f2.size > 24:
                f2 = font(f2.size - 2)
            bw = text_w(d, b, f2) + 64
            bxx = 108 + int((1 - clamp((local - 0.55 - i * 0.5) / 0.4)) * 26)
            rounded(d, (bxx, y, bxx + bw, y + 64), 12,
                    fill=(4, 14, 10, int(168 * ba / 255)), outline=(120, 235, 175, int(150 * ba / 255)), width=1)
            d.ellipse((bxx + 20, y + 24, bxx + 36, y + 40), fill=(84, 220, 140, ba))
            d.text((bxx + 50, y + 13), b, font=f2, fill=(255, 255, 255, ba))
            y += 80
        if layout == "closing":
            ca = int(255 * clamp((local - 2.2) / 0.5))
            if ca > 0:
                cta = scene["cta"]
                ctf = font(66)
                cw = text_w(d, cta, ctf) + 110
                cx, cy = (W - cw) / 2, y + 18
                rounded(d, (cx, cy, cx + cw, cy + 104), 18,
                        fill=(10, 46, 26, int(225 * ca / 255)),
                        outline=(120, 255, 180, ca), width=3)
                d.text((cx + (cw - text_w(d, cta, ctf)) / 2, cy + 16), cta,
                       font=ctf, fill=(255, 255, 255, ca))
                lf = font(30)
                label = "まずはガイドを開こう ─ 遊び方本「Mifron はじめの一歩」"
                d.text(((W - text_w(d, label, lf)) / 2, cy + 122), label,
                       font=lf, fill=(200, 255, 220, ca))

    # footer: caption + progress
    draw_caption(frame, scene["caption"])
    prog = clamp(global_t / max(total, 0.01))
    d2 = ImageDraw.Draw(frame, "RGBA")
    d2.rectangle((0, H - 6, int(W * prog), H), fill=(84, 220, 140, 255))
    # scene fade to black at edges
    if edge < 1.0:
        black = Image.new("RGBA", (W, H), (0, 0, 0, int(255 * (1 - edge))))
        frame.alpha_composite(black)
    return frame.convert("RGB")

# ---------------------------------------------------------------- main
def main():
    durations = [probe_duration(p) for p in NARRATION]
    print("narration durations:", [round(d, 2) for d in durations], flush=True)
    scene_durs = []
    for i, d in enumerate(durations):
        tail = 2.4 if i == len(durations) - 1 else TAIL_PAD
        scene_durs.append(HEAD_PAD + d + tail)
    total = sum(scene_durs)
    print(f"scene durations: {[round(d,2) for d in scene_durs]} total={total:.2f}s", flush=True)

    starts = []
    acc = 0.0
    for d in scene_durs:
        starts.append(acc)
        acc += d

    bgs = {}
    for s in SCENES:
        if s["bg"] not in bgs:
            bgs[s["bg"]] = load_bg(s["bg"])

    silent = os.path.join(ROOT, "_silent.mp4")
    cmd = [FFMPEG, "-y", "-f", "rawvideo", "-pix_fmt", "rgb24",
           "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
           "-an", "-c:v", "libx264", "-preset", "medium", "-crf", "19",
           "-pix_fmt", "yuv420p", silent]
    proc = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    total_frames = int(total * FPS)
    thumb_done = False
    try:
        for fi in range(total_frames):
            gt = fi / FPS
            # find scene
            si = 0
            for i, st in enumerate(starts):
                if gt >= st:
                    si = i
            local = gt - starts[si]
            frame = render_frame(SCENES[si], bgs[SCENES[si]["bg"]], local,
                                 scene_durs[si], gt, total)
            if not thumb_done and si == 0 and local > 1.1:
                frame.resize((1280, 720), Image.LANCZOS).save(
                    os.path.join(ROOT, "thumbnail.png"))
                thumb_done = True
            proc.stdin.write(frame.tobytes())
            if fi % (FPS * 5) == 0:
                print(f"frame {fi}/{total_frames} ({gt:.1f}s) scene {SCENES[si]['id']}", flush=True)
    finally:
        proc.stdin.close()
        proc.wait()
    if proc.returncode != 0:
        raise RuntimeError("ffmpeg video encode failed")

    bgm_path = os.path.join(ROOT, "_bgm.wav")
    synth_bgm(bgm_path, total)
    print("bgm done", flush=True)

    final = os.path.join(ROOT, "mifron-pr.mp4")
    inputs = ["-i", silent, "-i", bgm_path]
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
        "-t", f"{total:.3f}", "-movflags", "+faststart", final]
    print("mixing audio...", flush=True)
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stderr[-3000:])
        raise RuntimeError("ffmpeg audio mix failed")
    os.remove(silent)
    os.remove(bgm_path)
    print(f"DONE {final} total={total:.2f}s", flush=True)

if __name__ == "__main__":
    main()
