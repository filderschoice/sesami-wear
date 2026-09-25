"""実機で撮った掲載用スクリーンショットの、実機の状態が写る部分を隠して掲載サイズへ整える。

使い方は同じディレクトリの SKILL.md を参照。標準ライブラリと Pillow だけで動く。

  # 残す枠（ウィジェット・ダイアログ）の境界を探す: 指定した点の色と同じ色が続く区間を行・列ごとに出す
  python mask_screenshot.py regions captures/x.raw.png --at 50,650 --row 900 --col 50

  # 加工する
  python mask_screenshot.py mask captures/x.raw.png docs/store/images/screenshots/x.png \
      --status-bar 151 --dock-top 2700 --keep 32,594,1312,1376 --keep 692,192,1312,544 \
      --preview <scratchpad>/x_s.png
"""

import argparse

from PIL import Image, ImageDraw, ImageFilter

BAND = (30, 58, 95)  # #1E3A5F。既存の掲載画像と同じ帯色


def parse_box(text):
    box = tuple(int(v) for v in text.split(","))
    if len(box) != 4:
        raise argparse.ArgumentTypeError("x0,y0,x1,y1 の4つで指定する")
    return box


def color_runs(values, color, tolerance, min_len=8):
    runs, start = [], None
    for i, v in enumerate(values + [None]):
        near = v is not None and sum(abs(a - b) for a, b in zip(v, color)) < tolerance
        if near and start is None:
            start = i
        elif not near and start is not None:
            if i - 1 - start > min_len:
                runs.append((start, i - 1))
            start = None
    return runs


def cmd_regions(args):
    im = Image.open(args.src).convert("RGB")
    x, y = (int(v) for v in args.at.split(","))
    color = im.getpixel((x, y))
    print(f"size={im.size} color@{x},{y}={color}")
    for row in args.row:
        print(f"row {row}:", color_runs([im.getpixel((i, row)) for i in range(im.width)], color, args.tolerance))
    for col in args.col:
        print(f"col {col}:", color_runs([im.getpixel((col, i)) for i in range(im.height)], color, args.tolerance))


def pixelate(im, box, block):
    region = im.crop(box)
    w, h = region.size
    small = region.resize((max(1, w // block), max(1, h // block)), Image.BILINEAR)
    im.paste(small.resize((w, h), Image.NEAREST), box[:2])


def cmd_mask(args):
    src = Image.open(args.src).convert("RGB")
    out = src.copy()
    if args.keep and not args.no_blur:
        # 残す枠の外（壁紙・ほかのアイコン）をぼかし、枠の中だけ元画像を戻す
        out = src.filter(ImageFilter.GaussianBlur(args.blur))
        mask = Image.new("L", src.size, 0)
        draw = ImageDraw.Draw(mask)
        for box in args.keep:
            draw.rounded_rectangle(box, radius=args.radius, fill=255)
        out.paste(src, (0, 0), mask)
    if args.status_bar:
        pixelate(out, (0, 0, src.width, args.status_bar), args.block)
    if args.dock_top:
        pixelate(out, (0, args.dock_top, src.width, src.height), args.block)

    h = args.height
    w = round(src.width * h / src.height)
    scaled = out.resize((w, h), Image.LANCZOS)
    tw = round(h * 9 / 16)
    canvas = Image.new("RGB", (tw, h), BAND)
    canvas.paste(scaled, ((tw - w) // 2, 0))
    canvas.save(args.dst, optimize=True)
    if args.preview:
        canvas.resize((tw // 5, h // 5)).save(args.preview)
    print(f"{args.dst}: {canvas.size}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)

    regions = sub.add_parser("regions", help="残す枠の境界を探す")
    regions.add_argument("src")
    regions.add_argument("--at", required=True, help="枠の背景色を採る点 x,y")
    regions.add_argument("--row", type=int, action="append", default=[])
    regions.add_argument("--col", type=int, action="append", default=[])
    regions.add_argument("--tolerance", type=int, default=12)
    regions.set_defaults(func=cmd_regions)

    mask = sub.add_parser("mask", help="モザイク・ぼかし・9:16化")
    mask.add_argument("src")
    mask.add_argument("dst")
    mask.add_argument("--status-bar", type=int, default=0, help="ステータスバーの下端y（0で処理しない）")
    mask.add_argument("--dock-top", type=int, default=0, help="ドックの上端y。ここから下端までをモザイク（0で処理しない）")
    mask.add_argument("--keep", type=parse_box, action="append", default=[], help="ぼかさずに残す枠 x0,y0,x1,y1")
    mask.add_argument("--no-blur", action="store_true", help="壁紙をぼかさない（アプリ内の画面など）")
    mask.add_argument("--blur", type=int, default=40)
    mask.add_argument("--radius", type=int, default=48, help="残す枠の角丸の半径")
    mask.add_argument("--block", type=int, default=36, help="モザイクの1マスの大きさ")
    mask.add_argument("--height", type=int, default=2400)
    mask.add_argument("--preview", help="確認用の縮小版（1/5）の保存先")
    mask.set_defaults(func=cmd_mask)

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
