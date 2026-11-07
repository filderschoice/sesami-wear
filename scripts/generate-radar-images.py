"""「Bluetooth接続を確認」（レーダー）画面の画面イメージPNGを生成する（BL-277）。

実機・エミュレータでの撮影ではなく、実装の寸法・色・文言を転記して描いた**イメージ画像**である
（実画面のスクリーンショットではない）。実Sesameを要さず、何度でも同じ結果で作り直せる。
サイト（site/）や利用者向けドキュメントで画面の雰囲気を示す用途を想定している。

転記元（実装を変えたらここも追随させる）:

- 距離の換算: core/.../ble/SesameBleProximity.kt（1m地点のRSSIと減衰係数は、定数をソースから読む）
- 段階名・短縮名: core/.../ble/SesameBleProbeStage.kt
- スマートフォンの画面: mobile/.../radar/BleRadarActivity.kt / BleRadarCanvas.kt（ダークテーマ）
- ウォッチの画面: wear/.../radar/BleRadarActivity.kt / BleRadarCanvas.kt

出力（site/assets/）:

- radar-phone-{scanning,found,connected,notfound}.png  450x800（既存のスマートフォン画像と同じ9:16、帯 #1E3A5F）
- radar-wear-{scanning,found,connected,notfound}.png   384x384（既存のウォッチ画像と同じ）

使い方:

    python scripts/generate-radar-images.py            # site/assets/ へ出力
    python scripts/generate-radar-images.py <出力先>   # 別のディレクトリへ出力

日本語フォントが要る。Windowsの標準フォント（Noto Sans JP / 游ゴシック / メイリオ）を探す。
別のフォントを使うときは環境変数 RADAR_FONT_REGULAR / RADAR_FONT_BOLD でパスを指定する。
PIL（Pillow）が必要（`pip install pillow`）。ブリップの向きに意味は無い（実装でもデバイスごとの固定値）。
"""

import math
import os
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
PROXIMITY_SOURCE = ROOT / "core/src/main/kotlin/com/sesamiwear/core/ble/SesameBleProximity.kt"
SUPERSAMPLE = 4
SAMPLE_UUID = "5a8f3c2e-7b1d-4e9a-8c3f-0123456789ab"

STAGE_LABELS = {
    "SCANNING": ("探索中", "探索中"),
    "FOUND": ("発見", "発見"),
    "CONNECTED": ("接続OK", "接続OK"),
    "NOT_FOUND": ("圏外", "圏外"),
}

# 状態ごとの入力（段階、平滑化後のRSSI、接続確認の結果）。
STATES = {
    "scanning": {"stage": "SCANNING", "rssi": None, "check": None},
    "found": {"stage": "FOUND", "rssi": -71, "check": None},
    "connected": {"stage": "FOUND", "rssi": -71, "check": "接続OK（電池85%・角度12）"},
    "notfound": {"stage": "NOT_FOUND", "rssi": None, "check": None},
}

OFFICIAL_APP_HINT = "セサミ公式アプリを開いていると、そのアプリがBluetoothの接続を掴み、こちらから見つけられないことがあります。公式アプリを終了してからお試しください。"

# ダーク（Material3の既定の地の色 + SesameThemeの主色）。
PHONE = {
    "bg": (20, 18, 24),
    "band": (30, 58, 95),
    "on_surface": (230, 224, 233),
    "on_variant": (202, 196, 208),
    "outline": (147, 143, 153),
    "ring": (73, 69, 79),
    "primary": (164, 200, 255),
    "on_primary": (0, 49, 94),
    "error": (242, 184, 181),
}
WEAR = {"primary": (174, 203, 250), "error": (208, 55, 45), "text": (255, 255, 255), "sub": (225, 225, 225)}
COLOR_GOOD = (46, 125, 50)
COLOR_WARN = (239, 108, 0)
COLOR_WARN_WEAR = (255, 167, 38)
COLOR_GOOD_WEAR = (76, 175, 80)


def read_proximity_constants():
    text = PROXIMITY_SOURCE.read_text(encoding="utf-8")
    reference = int(re.search(r"REFERENCE_RSSI_AT_1M_DBM\s*=\s*(-?\d+)", text).group(1))
    exponent = float(re.search(r"PATH_LOSS_EXPONENT\s*=\s*([\d.]+)", text).group(1))
    return reference, exponent


REFERENCE_RSSI, PATH_LOSS = read_proximity_constants()
RINGS = [1, 3, 5, 10]


def distance_meters(rssi):
    return max(1.0, 10 ** ((REFERENCE_RSSI - rssi) / (10 * PATH_LOSS)))


def level(rssi):
    if rssi is None:
        return "NOT_SEEN"
    d = distance_meters(rssi)
    if d <= 1.5:
        return "VERY_NEAR"
    if d <= 4.0:
        return "NEAR"
    return "MEDIUM" if d <= 10.0 else "FAR"


LEVEL_LABELS = {"VERY_NEAR": "とても近い", "NEAR": "近い", "MEDIUM": "やや遠い", "FAR": "遠い"}


def distance_label(rssi):
    d = distance_meters(rssi)
    return f"{RINGS[-1]}m以上" if d > RINGS[-1] else f"約{round(d)}m"


def radar_fraction(rssi):
    if rssi is None:
        return None
    d = distance_meters(rssi)
    if d >= RINGS[-1]:
        return 1.0
    if d <= RINGS[0]:
        return 0.0
    upper = next(i for i, r in enumerate(RINGS) if d <= r)
    within = (d - RINGS[upper - 1]) / (RINGS[upper] - RINGS[upper - 1])
    return (upper + within) / len(RINGS)


def bearing(uuid):
    value = 0
    for ch in uuid:
        value = (value * 31 + ord(ch)) & 0xFFFFFFFF
    return (value % 360) / 360 * 2 * math.pi


def load_font(size, bold):
    override = os.environ.get("RADAR_FONT_BOLD" if bold else "RADAR_FONT_REGULAR")
    fonts = Path("C:/Windows/Fonts")
    candidates = [override] if override else []
    candidates += [
        fonts / "NotoSansJP-VF.ttf",
        fonts / ("YuGothB.ttc" if bold else "YuGothR.ttc"),
        fonts / ("meiryob.ttc" if bold else "meiryo.ttc"),
    ]
    for candidate in candidates:
        if candidate and Path(candidate).exists():
            font = ImageFont.truetype(str(candidate), size)
            try:
                font.set_variation_by_axes([700 if bold else 400])
            except (OSError, AttributeError):
                pass  # 可変フォントでなければそのまま使う
            return font
    sys.exit("日本語フォントが見つかりません。RADAR_FONT_REGULAR / RADAR_FONT_BOLD でパスを指定してください")


def wrap(draw, text, font, width):
    lines, current = [], ""
    for ch in text:
        if draw.textlength(current + ch, font=font) > width and current:
            lines.append(current)
            current = ch
        else:
            current += ch
    return lines + ([current] if current else [])


def blip_color(stage, rssi, wear):
    lv = level(rssi)
    if lv in ("VERY_NEAR", "NEAR"):
        return COLOR_GOOD_WEAR if wear else COLOR_GOOD
    if lv == "MEDIUM":
        return COLOR_WARN_WEAR if wear else COLOR_WARN
    return (158, 158, 158) if wear else PHONE["outline"]


def draw_radar(img, cx, cy, radius, unit, state, wear, ring_color, sweep_color, dot_color, label_font=None):
    """レーダー（輪・掃引線・ブリップ）を描く。unitは1dpあたりのpx。"""
    rgba = img.convert("RGBA")
    layer = Image.new("RGBA", rgba.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    # 掃引（先端から手前90度へ、先端ほど濃い）。
    sweep_angle = 300
    steps = 90
    for i in range(steps):
        start = sweep_angle - 90 + i
        alpha = int(255 * 0.45 * (i + 1) / steps)
        d.pieslice([cx - radius, cy - radius, cx + radius, cy + radius], start, start + 1.5, fill=sweep_color + (alpha,))
    rad = math.radians(sweep_angle)
    d.line([cx, cy, cx + radius * math.cos(rad), cy + radius * math.sin(rad)], fill=sweep_color + (255,), width=max(1, int(2 * unit)))
    # 輪（掃引の上に重ねる）。
    for index in range(1, len(RINGS) + 1):
        r = radius * index / len(RINGS)
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=ring_color + (255,), width=max(1, int(unit)))
        if label_font is not None:
            d.text((cx + 4 * unit, cy - r), f"{RINGS[index - 1]}m", font=label_font, fill=PHONE["on_variant"] + (255,))
    dot = (4 if not wear else 3) * unit
    d.ellipse([cx - dot, cy - dot, cx + dot, cy + dot], fill=dot_color + (255,))
    fraction = radar_fraction(state["rssi"])
    if fraction is not None:
        angle = bearing(SAMPLE_UUID)
        bx, by = cx + radius * fraction * math.cos(angle), cy + radius * fraction * math.sin(angle)
        base = (5 if wear else 9) * unit
        color = blip_color(state["stage"], state["rssi"], wear)
        halo = base * (2.0 if wear else 1.8)
        d.ellipse([bx - halo, by - halo, bx + halo, by + halo], fill=color + (int(255 * (0.3 if wear else 0.32)),))
        d.ellipse([bx - base, by - base, bx + base, by + base], fill=color + (255,))
    return Image.alpha_composite(rgba, layer).convert("RGB")


def render_phone(name, state):
    s = SUPERSAMPLE
    w, h = 450 * s, 800 * s
    img = Image.new("RGB", (w, h), PHONE["band"])
    left, right = 45 * s, 405 * s
    ImageDraw.Draw(img).rectangle([left, 0, right, h], fill=PHONE["bg"])
    unit = (360 / 411) * s  # 1dpあたりのpx（実機のスクリーンショットと同じ縮尺）
    cx = (left + right) / 2
    dp = lambda v: v * unit  # noqa: E731
    d = ImageDraw.Draw(img)
    f_title, f_large = load_font(int(dp(24)), False), load_font(int(dp(22)), False)
    f_body, f_small = load_font(int(dp(14)), False), load_font(int(dp(12)), False)
    f_label, f_button = load_font(int(dp(10)), False), load_font(int(dp(14)), True)

    d.text((cx, dp(64)), "玄関", font=f_title, fill=PHONE["on_surface"], anchor="mm")
    radius = dp(363 / 2 - 8)
    radar_cy = dp(88 + 363 / 2)
    img = draw_radar(img, cx, radar_cy, radius, unit, state, False, PHONE["ring"], PHONE["primary"], PHONE["on_variant"], f_label)
    d = ImageDraw.Draw(img)

    label = STAGE_LABELS[state["stage"]][0]
    problem = state["stage"] == "NOT_FOUND"
    summary = label if state["rssi"] is None else f"{label}・{distance_label(state['rssi'])}（{LEVEL_LABELS[level(state['rssi'])]}）"
    y = dp(88 + 363 + 12 + 14)
    d.text((cx, y), summary, font=f_large, fill=PHONE["error"] if problem else PHONE["on_surface"], anchor="mm")
    y += dp(36)
    if state["rssi"] is not None:
        d.text((cx, y), f"電波強度 {state['rssi']}dBm", font=f_body, fill=PHONE["on_surface"], anchor="mm")
        y += dp(30)
    if state["check"]:
        d.text((cx, y), f"接続確認：{state['check']}", font=load_font(int(dp(16)), False), fill=PHONE["primary"], anchor="mm")
        y += dp(30)
    text_left, text_width = left + dp(24), (right - left) - dp(48)
    notes = []
    if problem:
        notes.append("電波が見つかりません（探索は続けています）。" + OFFICIAL_APP_HINT)
    notes.append("距離は電波の強さからの目安です。壁・人・向きで大きく変わります。")
    for note in notes:
        for line in wrap(d, note, f_small, text_width):
            d.text((text_left if problem and len(notes) > 1 and note is notes[0] else cx, y), line, font=f_small,
                   fill=PHONE["on_variant"], anchor="lm" if problem and note is notes[0] else "mm")
            y += dp(17)
        y += dp(8)
    # ボタン（閉じる＝枠線、接続を試す＝塗り）。
    by = y + dp(20)
    bw, bh, gap = dp(96), dp(40), dp(12)
    x0 = cx - (bw + gap + dp(120)) / 2
    d.rounded_rectangle([x0, by, x0 + bw, by + bh], radius=bh / 2, outline=PHONE["outline"], width=max(1, int(unit)))
    d.text((x0 + bw / 2, by + bh / 2), "閉じる", font=f_button, fill=PHONE["primary"], anchor="mm")
    x1 = x0 + bw + gap
    d.rounded_rectangle([x1, by, x1 + dp(120), by + bh], radius=bh / 2, fill=PHONE["primary"])
    d.text((x1 + dp(60), by + bh / 2), "接続を試す", font=f_button, fill=PHONE["on_primary"], anchor="mm")
    return img.resize((450, 800), Image.LANCZOS)


def render_wear(name, state):
    s = SUPERSAMPLE
    size = 384 * s
    unit = 2 * s  # 384px / 192dp
    img = Image.new("RGB", (size, size), (0, 0, 0))
    cx = size / 2
    radar_w = 0.62 * 192 * unit
    top = 18 * unit
    radius = radar_w / 2 - 2 * unit
    cy = top + radar_w / 2
    img = draw_radar(img, cx, cy, radius, unit, state, True, (89, 89, 89), WEAR["primary"], (255, 255, 255))
    d = ImageDraw.Draw(img)
    stage = state["stage"]
    problem = stage == "NOT_FOUND"
    first = STAGE_LABELS[stage][1]
    second = state["check"].split("（")[1].rstrip("）") if state["check"] else (distance_label(state["rssi"]) if state["rssi"] is not None else "タップで接続")
    if state["check"]:
        first = STAGE_LABELS["CONNECTED"][1]
    title_y = top + radar_w + 12 * unit
    d.text((cx, title_y), first, font=load_font(int(16 * unit), True), fill=WEAR["error"] if problem else WEAR["text"], anchor="mm")
    d.text((cx, title_y + 18 * unit), second, font=load_font(int(11 * unit), False), fill=WEAR["sub"], anchor="mm")
    # 円形の画面へ切り抜く（外側は黒。既存のウォッチ画像と同じ）。
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    out = Image.new("RGB", (size, size), (0, 0, 0))
    out.paste(img, mask=mask)
    return out.resize((384, 384), Image.LANCZOS)


def main():
    out_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "site/assets"
    out_dir.mkdir(parents=True, exist_ok=True)
    for name, state in STATES.items():
        render_phone(name, state).save(out_dir / f"radar-phone-{name}.png", optimize=True)
        render_wear(name, state).save(out_dir / f"radar-wear-{name}.png", optimize=True)
        print(f"generated radar-phone-{name}.png / radar-wear-{name}.png")


if __name__ == "__main__":
    main()
