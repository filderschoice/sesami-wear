"""ウィジェット一覧（ウィジェットピッカー）に出すプレビュー画像を、ストア掲載用のスクリーンショットから作る（BL-226）。

入力は `docs/store/images/screenshots/phone_1_widget_locked.png`（既定）。この画像には、上に2 × 1版、
下に4 × 2版のウィジェットが縦に並んで写っている（撮影手順は Skill `store-screenshot-capture` /
`store-screenshot-masking`）。ウィジェットの背景色（#121212）の画素が多い行の塊を上下2つ探し、
それぞれの左右の範囲を求めて切り出す。角丸の外側（壁紙が写っている部分）は透明にする。

出力（既定）:

- `mobile/src/main/res/drawable-nodpi/widget_preview.png`（4 × 2版、`sesame_widget_info.xml` の previewImage）
- `mobile/src/main/res/drawable-nodpi/widget_preview_small.png`（2 × 1版、`sesame_widget_small_info.xml`）

使い方（リポジトリのルートで実行する。Pillow が必要）:

    python scripts/make-widget-previews.py
    python scripts/make-widget-previews.py --source <スクリーンショット> --out-dir <出力先>

ウィジェットの見た目を変えて掲載用スクリーンショットを撮り直したら、本スクリプトで作り直す。
2つの塊が見つからない場合は終了コード1で止まる（画像の構図が変わった場合）。
"""

import argparse
import math
import os
import sys

from PIL import Image, ImageChops, ImageDraw

SOURCE = "docs/store/images/screenshots/phone_1_widget_locked.png"
OUT_DIR = "mobile/src/main/res/drawable-nodpi"
WIDGET_BACKGROUND = (0x12, 0x12, 0x12)  # SesameWidget の WIDGET_BACKGROUND_ARGB
TOLERANCE = 4  # 各チャンネルの許容差
MIN_PIXELS_PER_LINE = 20  # 行・列を「ウィジェットの一部」とみなす背景色の画素数
MIN_BAND_HEIGHT = 100  # 行の塊をウィジェットとみなす最小の高さ（px）
MIN_RADIUS, MAX_RADIUS = 8, 80  # 角丸の半径の探索範囲（px）


def background_mask(image):
    """ウィジェットの背景色に近い画素を255、それ以外を0にした1チャンネル画像を返す。"""
    channels = [
        band.point(lambda v, c=c: 255 if abs(v - c) <= TOLERANCE else 0)
        for band, c in zip(image.split(), WIDGET_BACKGROUND)
    ]
    # 3チャンネルすべてが近い画素だけを残す（最小値をとる）
    return ImageChops.darker(ImageChops.darker(channels[0], channels[1]), channels[2])


def count_line(mask, box):
    return mask.crop(box).histogram()[255]


def find_bands(mask):
    """背景色の画素が多い行の連続区間（上から順）を返す。"""
    width, height = mask.size
    bands, start = [], None
    for y in range(height + 1):
        hit = y < height and count_line(mask, (0, y, width, y + 1)) > MIN_PIXELS_PER_LINE
        if hit and start is None:
            start = y
        elif not hit and start is not None:
            if y - start >= MIN_BAND_HEIGHT:
                bands.append((start, y - 1))
            start = None
    return bands


def horizontal_range(mask, top, bottom):
    width = mask.size[0]
    columns = [x for x in range(width) if count_line(mask, (x, top, x + 1, bottom + 1)) > MIN_PIXELS_PER_LINE]
    return columns[0], columns[-1]


def corner_radius(mask, left, top):
    """左上の角で、各行の背景色が始まる位置（左端からのずれ）に円弧を当てはめて半径（px）を見積もる。

    半径 r の角丸では、上端から dy 行目の左端からのずれは r − √(r² − (r − dy − 0.5)²) になる。
    縁の画素は壁紙と混ざって背景色から外れるため、1行だけで決めず誤差の二乗和が最小の r を選ぶ。
    """
    offsets = []
    for dy in range(MAX_RADIUS):
        row = [x for x in range(left, left + MAX_RADIUS) if mask.getpixel((x, top + dy)) == 255]
        if row:
            offsets.append((dy, row[0] - left))

    def error(r):
        total = 0.0
        for dy, offset in offsets:
            if dy >= r:
                break
            expected = r - math.sqrt(max(0.0, r * r - (r - dy - 0.5) ** 2))
            total += (expected - offset) ** 2
        return total / max(1, min(r, len(offsets)))

    return min(range(MIN_RADIUS, MAX_RADIUS), key=error)


def cut_widget(image, mask, top, bottom):
    left, right = horizontal_range(mask, top, bottom)
    radius = corner_radius(mask, left, top)
    widget = image.crop((left, top, right + 1, bottom + 1)).convert("RGBA")
    # 2倍の大きさで角丸を描いてから縮め、縁を滑らかにする
    scale = 2
    alpha = Image.new("L", (widget.width * scale, widget.height * scale), 0)
    ImageDraw.Draw(alpha).rounded_rectangle(
        (0, 0, alpha.width - 1, alpha.height - 1), radius=radius * scale, fill=255
    )
    widget.putalpha(alpha.resize(widget.size, Image.LANCZOS))
    return widget, (left, top, right, bottom, radius)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--source", default=SOURCE)
    parser.add_argument("--out-dir", default=OUT_DIR)
    args = parser.parse_args()

    image = Image.open(args.source).convert("RGB")
    mask = background_mask(image)
    bands = find_bands(mask)
    if len(bands) != 2:
        print(f"ウィジェットの塊が2つ見つからない（{len(bands)}個）: {bands}", file=sys.stderr)
        return 1

    os.makedirs(args.out_dir, exist_ok=True)
    # 上が2 × 1版、下が4 × 2版
    for (top, bottom), name in zip(bands, ["widget_preview_small.png", "widget_preview.png"]):
        widget, info = cut_widget(image, mask, top, bottom)
        path = os.path.join(args.out_dir, name)
        widget.save(path, optimize=True)
        print(f"{path}: {widget.width}x{widget.height} (left,top,right,bottom,radius)={info}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
