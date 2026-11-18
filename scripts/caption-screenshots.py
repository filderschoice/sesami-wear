"""スマートフォン用の掲載画像へ機能の説明文を入れた版を作る（BL-286）。

入力は `docs/store/images/screenshots/phone_1〜phone_8`（1350x2400）。出力は
`docs/store/images/screenshots_captioned/` へ同じファイル名で保存する（元画像は変更しない。
`site/` と `scripts/make-widget-previews.py` が元画像を使うため）。Wear OS用（wear_*）は対象外。

配置: 上端の高さ400pxを帯色 #1E3A5F の説明文の領域とし、白の太字で1〜2行を中央に置く。
元画像は高さ2000pxへ縮小して下側の中央へ置き、余白は同じ帯色で埋める。出力は24bit PNG・1350x2400。

フォントは同梱しない。既定は Windows 標準の Noto Sans JP（可変フォント）で、無ければエラーで止まる。
使い方（リポジトリのルートで実行する。Pillow が必要）:

    python scripts/caption-screenshots.py
    python scripts/caption-screenshots.py --font <フォントファイル> --out-dir <出力先>

説明文は下の CAPTIONS で持つ。1行は全角14文字程度までとし、半角空白の位置で改行する。
順位・性能の誇張、価格、行動喚起、ストアのバッジは入れない。
"""

import argparse
import os
import sys

from PIL import Image, ImageDraw, ImageFont

SOURCE_DIR = "docs/store/images/screenshots"
OUT_DIR = "docs/store/images/screenshots_captioned"
DEFAULT_FONT = "C:/Windows/Fonts/NotoSansJP-VF.ttf"
BAND_COLOR = (0x1E, 0x3A, 0x5F)
TEXT_COLOR = (255, 255, 255)
SIZE = (1350, 2400)
CAPTION_HEIGHT = 400
IMAGE_HEIGHT = SIZE[1] - CAPTION_HEIGHT
FONT_SIZE = 84
LINE_GAP = 20
SIDE_MARGIN = 60

# 半角空白は改行してよい位置。表示では空白で2行に分ける。
CAPTIONS = {
    "phone_1_widget_locked.png": "ホーム画面から すぐに施錠・解錠",
    "phone_2_widget_unlock_confirm.png": "解錠は確認してから実行",
    "phone_3_widget_unlocked.png": "1台ずつ・全台まとめて 状態がひと目で分かる",
    "phone_4_device_list.png": "セサミごとの状態と 電池を一覧で確認",
    "phone_5_menu.png": "操作の経路や通知を設定",
    "phone_6_radar.png": "Bluetoothの届き具合を レーダーで確認",
    "phone_7_radar_all.png": "全台の接続をまとめて確認",
    "phone_8_dial.png": "ダイヤルをなぞって Bluetoothで直接操作",
}


def load_font(path):
    if not os.path.isfile(path):
        sys.exit(f"フォントが見つかりません: {path}（--font で指定してください）")
    font = ImageFont.truetype(path, FONT_SIZE)
    try:
        font.set_variation_by_axes([700])  # 可変フォントの太さ(wght)を太字へ
    except (OSError, AttributeError) as e:  # 固定ウェイトのフォント等。太字にできなくても続行する
        print(f"警告: 太字にできませんでした（{type(e).__name__}）。標準の太さで続行します: {path}", file=sys.stderr)
    return font


def split_lines(draw, text, font):
    """全体が幅に収まれば1行、収まらなければ半角空白で最も均等に2行へ分ける。"""
    limit = SIZE[0] - SIDE_MARGIN * 2
    if draw.textlength(text, font=font) <= limit:
        return [text]
    words = text.split(" ")
    best = None
    for i in range(1, len(words)):
        lines = [" ".join(words[:i]), " ".join(words[i:])]
        width = max(draw.textlength(line, font=font) for line in lines)
        if best is None or width < best[0]:
            best = (width, lines)
    if best is None or best[0] > limit:
        sys.exit(f"説明文が幅に収まりません（空白の位置を見直してください）: {text}")
    return best[1]


def caption_one(src_path, text, font):
    canvas = Image.new("RGB", SIZE, BAND_COLOR)
    src = Image.open(src_path).convert("RGB")
    width = round(src.width * IMAGE_HEIGHT / src.height)
    canvas.paste(src.resize((width, IMAGE_HEIGHT), Image.LANCZOS), ((SIZE[0] - width) // 2, CAPTION_HEIGHT))
    draw = ImageDraw.Draw(canvas)
    lines = split_lines(draw, text, font)
    line_h = font.getbbox("あ")[3] - font.getbbox("あ")[1]
    total = line_h * len(lines) + LINE_GAP * (len(lines) - 1)
    y = (CAPTION_HEIGHT - total) // 2 - font.getbbox("あ")[1]
    for line in lines:
        x = (SIZE[0] - draw.textlength(line, font=font)) / 2
        draw.text((x, y), line, font=font, fill=TEXT_COLOR)
        y += line_h + LINE_GAP
    return canvas


def check_sources(source_dir):
    """全入力の存在とサイズを、書き出しの前にまとめて検査する（途中で止まって一部だけ出力されるのを防ぐ）。"""
    for name in CAPTIONS:
        src = os.path.join(source_dir, name)
        if not os.path.isfile(src):
            sys.exit(f"元画像が見つかりません: {src}")
        with Image.open(src) as im:
            if im.size != SIZE:
                sys.exit(f"元画像のサイズが {SIZE[0]}x{SIZE[1]} ではありません: {src}（{im.width}x{im.height}）")


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--source-dir", default=SOURCE_DIR)
    parser.add_argument("--out-dir", default=OUT_DIR)
    parser.add_argument("--font", default=DEFAULT_FONT)
    args = parser.parse_args()
    if os.path.realpath(args.out_dir) == os.path.realpath(args.source_dir):
        sys.exit(f"--out-dir が --source-dir と同じです（元画像の上書きを防ぐため止めます）: {args.out_dir}")
    font = load_font(args.font)
    check_sources(args.source_dir)
    os.makedirs(args.out_dir, exist_ok=True)
    for name, text in CAPTIONS.items():
        src = os.path.join(args.source_dir, name)
        caption_one(src, text, font).save(os.path.join(args.out_dir, name), format="PNG", optimize=True)
        print(f"{name}: {text}")


if __name__ == "__main__":
    main()
