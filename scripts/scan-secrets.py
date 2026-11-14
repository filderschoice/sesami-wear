"""コミット前の秘密情報スキャン（品質ゲート）。

guardrails 12.1 の「コミット前に秘密情報スキャンを実行し、資格情報の混入がないことを確認する」を
目視ではなく機械的に行う。既定ではステージ済みの差分（`git diff --cached`）の追加行と、
追加・変更・改名・種別変更（シンボリックリンクと通常ファイルの入れ替え等）されたファイルのパスを検査する。

検出するもの（技術スタックに依存しない汎用の規則）:

- 秘密鍵の PEM ヘッダ（BEGIN ... PRIVATE KEY の行）
- 形式で見分けられるトークン・鍵: AWS のアクセスキー ID、GitHub のトークン（`ghp_` などの接頭辞つき、
  fine-grained の `github_pat_`）、Google の API キー（`AIza` で始まる39文字）、Slack のトークン（`xox` で始まる）、
  `sk-` で始まる長い API キー、`sk_live_`・`rk_live_` で始まるキー、JWT（ヘッダとペイロードが `eyJ` で始まる3区画）、
  Azure などの接続文字列の `AccountKey=`・`SharedAccessKey=`
- 資格情報を表すキー名への文字列リテラルの代入。キー名は末尾が password・passwd・secret・token・api_key（apikey）・
  access_key・secret_key・private_key のもので、接頭辞は問わない（`DB_` や `spring.datasource.` が付いても検出する）。
  設定ファイル形式の拡張子と `.env` 系のファイルでは、引用符の無い値の代入も対象
- ファイル名が、鍵ストア（`.jks`・`.keystore`・`.p12`・`.pfx`・`.bks`）・鍵（`.pem`・`.key`、`id_rsa` 等の SSH 秘密鍵）・
  `credentials.json`・`.npmrc`・`.netrc`・`local.properties`（ローカル環境の設定や署名情報を置くファイル）・`.env`・
  `.env.*` のファイル（大文字小文字を区別しない）。資格情報を保持するため、除外設定（`.gitignore` 等）で
  バージョン管理から外しておくべきもの（guardrails 12.5）

検出しないもの（誤検出の抑制。理由は DESIGN.md「品質観点」）:

- 代入の値が別の値の参照だけでできているもの（`${名前}`、`$名前`、`{{ 名前 }}`、`%名前%`、既定値が空の `${名前:}` 等）。
  値そのものは書かれていないため。既定値に実際の値を書いた `${名前:値}`・`${名前:-値}`・`${名前-値}` は検出する
- `.env.example`・`.env.sample`・`.env.template` のファイル名（ひな形として管理するのが一般的なため）。
  中身の行は通常の規則で検査する

既知の誤検出: ファイル名の規則 `.pem`・`.key` は、公開証明書・公開鍵のファイルでも検出する（中身を見ずに名前で判定するため）。
規則は緩めず、検出時の案内に判断方法（中身に PRIVATE KEY の行が無いか確かめ、拡張子を変えるか人の判断を仰ぐ）を出す。

検出したらファイル名・行番号・規則名を出力し、値そのものは出力しない（ログへの再露出を防ぐ）。
正規表現は文字クラスや連結で組み立てており、本スクリプト自身の追加行には一致しない。
導入先の技術スタックに固有の資格情報（クラウドの鍵の形式、設定ファイル名など）は、CONTENT_RULES・FILE_NAME_RULES へ
規則を足して検出対象を広げる。検出を緩める変更（規則の削除・除外指定の追加）は統制を緩める変更として扱う。

使い方:

    git add <ファイル>
    python scripts/scan-secrets.py --require-changes                      # ステージ済みの差分を検査する
    python scripts/scan-secrets.py --include-unstaged                     # 未ステージの変更と未追跡ファイルも含める
    python scripts/scan-secrets.py --range main...HEAD --require-changes  # コミット範囲（ブランチ全体）を検査する

`--range` は `git diff <範囲>` に渡す（`main...HEAD` は main との分岐点から HEAD までの差分。規定ブランチが main で
なければ読み替える）。ループの最後や PR 作成前にブランチ全体を確かめるとき、`git add` できない読み取り専用の
品質ゲート実行担当（サブエージェント）が検査するときに使う。`--include-unstaged` とは併用できないため、
未コミットの変更とコミット範囲の両方を確かめるときは2回に分けて実行する。

終了コードは、検出なしで0、検出ありで1、git の実行に失敗したら2。検査対象の変更が1件も無いときは、
「検出なし」と区別できるよう標準エラーへ警告を出したうえで0を返す（ステージしない場面では `--include-unstaged`、
クリーンな作業ツリーでは `--range` で対象を指定し直す）。`--require-changes` を付けると、対象が空のときは0ではなく
2を返す。`git add` の後やループの最後・PR 作成前の `--range` など、検査対象があるはずの場面で付け、合否を
終了コードだけで判定できるようにする（警告文の読み取りに頼らない）。ファイルの削除だけの変更は検査対象に
含まれないため、削除だけをコミットする場面では付けずに実行する。標準ライブラリのみで動作する。
出力は UTF-8 に固定している。PowerShell でパイプ・変数へ受ける場合は、git の出力と同じく
`[Console]::OutputEncoding = [Text.Encoding]::UTF8` を設定してから実行する。
誤検出した場合は、値をリポジトリの外（環境変数や、除外設定でバージョン管理から外した設定ファイル）から読む形へ
書き換えるのが原則（本スクリプトに除外指定の仕組みは無い）。
"""

import argparse
import os
import re
import subprocess
import sys

# 内容の規則（規則名, 正規表現）。値を出力しないため、規則名だけで何が見つかったか分かる名前にする。
CONTENT_RULES = [
    ("秘密鍵のPEMヘッダ", re.compile("-{5}BEGIN [A-Z0-9 ]*PRIVATE KEY( BLOCK)?-{5}")),
    ("AWSアクセスキーID", re.compile(r"\b(AKIA|ASIA)[0-9A-Z]{16}\b")),
    ("AWSシークレットキーの代入", re.compile(r"(?i)aws_secret_access_key\s*[:=]\s*\S")),
    ("GitHubトークン", re.compile(r"\bgh[pousr]_[A-Za-z0-9]{36,}\b")),
    ("GitHubトークン(fine-grained)", re.compile(r"\bgithub_pat_[A-Za-z0-9_]{22,}")),
    ("GoogleのAPIキー", re.compile(r"\bAIza[0-9A-Za-z_\-]{35}")),
    ("Slackのトークン", re.compile(r"\bxox[abposr]-[0-9A-Za-z\-]{10,}")),
    ("sk-で始まるAPIキー", re.compile(r"\bsk-[0-9A-Za-z_\-]{32,}")),
    ("本番用の秘密キー(sk_live/rk_live)", re.compile(r"\b[rs]k_live_[0-9A-Za-z]{16,}")),
    ("JWT", re.compile(r"\beyJ[0-9A-Za-z_\-]{10,}\.eyJ[0-9A-Za-z_\-]{10,}\.[0-9A-Za-z_\-]{10,}")),
    ("接続文字列のアカウントキー", re.compile(r"(?i)\b(Account|SharedAccess)Key\s*=\s*[0-9A-Za-z+/]{20,}")),
]

# 資格情報を表すキー名（末尾で判定し、接頭辞は問わない）。
CREDENTIAL_KEY = r"[0-9A-Za-z_.\-]*(?:pass" + r"word|passwd|secret|token|(?:api|access|secret|private)[_\-]?key)"

# 引用符付きの値の代入。値は別に取り出して参照（${...} 等）を除外する。
QUOTED_ASSIGNMENT = re.compile(
    r"(?i)(?<![0-9A-Za-z_.\-])" + CREDENTIAL_KEY + r"""["']?\s*[:=]\s*(["'])([^"'\s][^"']*)\1"""
)

# 設定ファイル形式では値に引用符が付かないため、引用符の無い代入も検出する。
# ソースコードで `password = 変数` を誤検出しないよう、対象を拡張子とファイル名で絞る。
# 値の先頭が引用符のものは上の規則の対象のため除く。値は取り出して参照かどうかを判定する。
CONFIG_SUFFIXES = (".properties", ".ini", ".cfg", ".conf", ".toml", ".yml", ".yaml", ".env")
UNQUOTED_ASSIGNMENT = re.compile(r"(?i)^\s*(?:export\s+)?" + CREDENTIAL_KEY + r"""\s*[:=]\s*([^\s"'].*)$""")
TRAILING_COMMENT = re.compile(r"\s+#.*$")

# 値が別の値の参照だけでできていることを判定する形。次のいずれか（の連結）を参照とみなす。
# - `${名前}`、既定値が空の `${名前:}`・`${名前:-}`・`${名前-}` 等、未設定時のエラー文を持つ `${名前:?文}`
#   名前はシェルの変数名、またはドットを含むプロパティ名（`spring.datasource.password` 等。ハイフンを含んでよい）
# - `$名前`（シェルの変数名）、`{{ 名前 }}`、`%名前%`
# 既定値に実際の値を書いた `${名前:値}`・`${名前:-値}`・`${名前-値}` は参照とみなさない（値そのものが書かれているため）。
SHELL_NAME = r"[A-Za-z_][0-9A-Za-z_]*"
DOTTED_NAME = r"[0-9A-Za-z_\-]+(?:\.[0-9A-Za-z_\-]+)+"
REFERENCE_TOKEN = (
    r"\$\{(?:" + DOTTED_NAME + "|" + SHELL_NAME + r")(?::?[-=+]?|:?\?[^}]*)\}"
    r"|\$" + SHELL_NAME
    + r"|\{\{[^{}]*\}\}"
    r"|%" + SHELL_NAME + "%"
)
REFERENCE_VALUE = re.compile(r"(?:" + REFERENCE_TOKEN + r")+")

# ファイル名の規則。資格情報を保持するため、除外設定（.gitignore 等）でバージョン管理から外しておくべきもの（guardrails 12.5）。
FILE_NAME_RULES = [
    ("鍵ストアのファイル", re.compile(r"\.(jks|keystore|p12|pfx|bks)$", re.I)),
    ("鍵のファイル", re.compile(r"\.(pem|key)$", re.I)),
    ("SSHの秘密鍵", re.compile(r"^id_(rsa|dsa|ecdsa|ed25519)$", re.I)),
    ("資格情報のファイル", re.compile(r"^(credentials\.json|\.npmrc|\.netrc)$", re.I)),
    ("local.properties", re.compile(r"^local\.properties$", re.I)),
    ("環境変数ファイル", re.compile(r"^\.env(\.(?!(example|sample|template)$).+)?$", re.I)),
]

HUNK_HEADER = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@")


def run_git(args: list[str]) -> str:
    """git を実行して標準出力を返す。日本語のパスをそのまま得るため core.quotepath を切る。"""
    result = subprocess.run(
        ["git", "-c", "core.quotepath=false", *args],
        capture_output=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    if result.returncode != 0:
        # git の使い方の全文が続くことがあるため、先頭の1行だけを出す。
        first_line = (result.stderr.strip().splitlines() or ["(出力なし)"])[0]
        raise RuntimeError(f"git {' '.join(args)} が失敗しました: {first_line}")
    return result.stdout


def is_config_file(path: str) -> bool:
    name = os.path.basename(path).lower()
    return name.endswith(CONFIG_SUFFIXES) or name.startswith(".env")


def is_reference(value: str) -> bool:
    """値が別の値の参照だけでできているか（既定値に実際の値を書いた参照は含まない）。"""
    return REFERENCE_VALUE.fullmatch(value.strip()) is not None


def has_quoted_literal(text: str) -> bool:
    """資格情報のキー名へ、参照ではない文字列リテラルを代入している箇所があるか。"""
    return any(not is_reference(match.group(2)) for match in QUOTED_ASSIGNMENT.finditer(text))


def has_unquoted_literal(text: str) -> bool:
    """設定ファイルで、資格情報のキー名へ参照ではない値を引用符なしで代入しているか。行末のコメントは値に含めない。"""
    match = UNQUOTED_ASSIGNMENT.search(text)
    return match is not None and not is_reference(TRAILING_COMMENT.sub("", match.group(1)))


def scan_line(path: str, line_no: int, text: str, findings: list[str]) -> None:
    for rule_name, pattern in CONTENT_RULES:
        if pattern.search(text):
            findings.append(f"{path}:{line_no}: {rule_name}")
    if has_quoted_literal(text):
        findings.append(f"{path}:{line_no}: 資格情報のキーへのリテラル代入")
    elif is_config_file(path) and has_unquoted_literal(text):
        findings.append(f"{path}:{line_no}: 資格情報のキーへの代入（設定ファイル）")


def parse_target_path(header: str) -> str | None:
    """`+++ ` 見出しの後ろから変更後のパスを取り出す。削除（/dev/null）なら None。"""
    target = header[4:]
    # 空白を含むパスでは、git が見出しの末尾にタブを付ける。
    target = target.rstrip("\t")
    if target.startswith('"') and target.endswith('"'):
        # 制御文字などを含むパスは C 形式で引用される。検査結果の表示用なので引用符だけを外す。
        target = target[1:-1]
    if target == "/dev/null":
        return None
    return target[2:] if target.startswith("b/") else target


def scan_diff(diff_text: str, findings: list[str]) -> None:
    """unified diff（-U0）の追加行を検査する。行番号は変更後のファイルでの位置。

    `--- `・`+++ ` を見出しとして扱うのは `diff --git` 行から最初のハンクまでのヘッダ部だけにする。
    ハンクの中で `++ ` で始まる行を追加した場合も `+++ ` で始まるため、見出しと取り違えると検査から漏れる。
    """
    path = None
    line_no = 0
    in_header = False
    for line in diff_text.splitlines():
        if line.startswith("diff --git "):
            in_header = True
            path = None
            continue
        if in_header:
            if line.startswith("+++ "):
                path = parse_target_path(line)
                continue
            header = HUNK_HEADER.match(line)
            if header:
                in_header = False
                line_no = int(header.group(1))
            # それ以外のヘッダ行（index・mode・rename・--- など）は読み飛ばす。
            continue
        header = HUNK_HEADER.match(line)
        if header:
            line_no = int(header.group(1))
            continue
        if path is None:
            continue
        if line.startswith("+"):
            scan_line(path, line_no, line[1:], findings)
            line_no += 1
        elif line.startswith(" "):
            line_no += 1


def scan_untracked_file(path: str, findings: list[str]) -> None:
    try:
        with open(path, encoding="utf-8", errors="replace") as handle:
            for index, text in enumerate(handle, start=1):
                scan_line(path, index, text.rstrip("\n"), findings)
    except OSError as error:
        findings.append(f"{path}: 読み込めませんでした（{error.strerror}）")


def scan_file_names(paths: list[str], findings: list[str]) -> None:
    for path in paths:
        name = os.path.basename(path)
        for rule_name, pattern in FILE_NAME_RULES:
            if pattern.search(name):
                findings.append(f"{path}: {rule_name}（バージョン管理へ入れないファイル名）")


def main() -> int:
    parser = argparse.ArgumentParser(description="コミット前の秘密情報スキャン")
    parser.add_argument(
        "--include-unstaged",
        action="store_true",
        help="未ステージの変更（HEAD との差分）と未追跡ファイルも検査する",
    )
    parser.add_argument(
        "--range",
        metavar="範囲",
        help="ステージ済みの差分の代わりに、コミット範囲（例 main...HEAD）の差分を検査する",
    )
    parser.add_argument(
        "--require-changes",
        action="store_true",
        help="検査対象の変更が1件も無いとき、合格にせず終了コード2を返す（git add の後・--range など対象があるはずの場面で使う）",
    )
    options = parser.parse_args()
    if options.range is not None and (options.include_unstaged or not options.range or options.range.startswith("-")):
        print("--range にはコミット範囲（例 main...HEAD）を指定し、--include-unstaged とは併用しない", file=sys.stderr)
        return 2

    findings: list[str] = []
    # 利用者の設定（diff.noprefix 等）で見出しの接頭辞が変わらないよう、a/・b/ を明示する。
    # T（種別変更。シンボリックリンクから通常ファイルへ等）も含める。含めないと中身が丸ごと検査から漏れる。
    diff_base = ["diff", "--no-color", "--no-ext-diff", "--diff-filter=ACMRT", "--src-prefix=a/", "--dst-prefix=b/"]
    if options.range is not None:
        diff_base += [options.range]
    else:
        diff_base += ["HEAD"] if options.include_unstaged else ["--cached"]
    try:
        scan_diff(run_git([*diff_base, "-U0", "--"]), findings)
        changed = run_git([*diff_base, "--name-only", "--"]).splitlines()
        if options.include_unstaged:
            untracked = run_git(["ls-files", "--others", "--exclude-standard"]).splitlines()
            for path in untracked:
                scan_untracked_file(path, findings)
            changed += untracked
        scan_file_names(changed, findings)
    except RuntimeError as error:
        print(error, file=sys.stderr)
        return 2

    if options.range is not None:
        scope = f"コミット範囲 {options.range} "
    else:
        scope = "ステージ済み・未ステージ・未追跡" if options.include_unstaged else "ステージ済み"
    if findings:
        print(f"秘密情報の疑い: {len(findings)} 件（値は表示しません）")
        for finding in findings:
            print(f"  {finding}")
        print(
            "対処: 値をリポジトリの外（環境変数や、除外設定でバージョン管理から外した設定ファイル）から読む形へ直し、"
            "資格情報を持つファイルが除外設定（.gitignore 等）に含まれていることを確かめる（guardrails 12.5）"
        )
        if any("鍵のファイル" in finding for finding in findings):
            print(
                "鍵のファイル（.pem・.key）の検出は、公開証明書・公開鍵でも出る（既知の誤検出）。中身に PRIVATE KEY の行が"
                "無く公開してよいものなら、拡張子を中身に合う名前（.crt・.pub 等）へ変えられるか検討し、変えられなければ"
                "検出結果を人へ示してコミットしてよいかを判断してもらう（自律ループでは要確認として保留する）"
            )
        return 1
    if not changed:
        hint = (
            "指定した対象が意図どおりか確かめてください"
            if options.range is not None or options.include_unstaged
            else "ステージしていない場面では --include-unstaged、コミット済みの変更は --range <規定ブランチ>...HEAD で"
            "検査してください"
        )
        print(f"警告: 検査対象の変更がありません（対象: {scope}の変更）。{hint}", file=sys.stderr)
        print(f"秘密情報スキャン: 検査対象なし（対象: {scope}の変更）")
        if options.require_changes:
            print("NG: --require-changes を指定したが検査対象が空のため、合格にしない（終了コード2）", file=sys.stderr)
            return 2
        return 0
    print(f"秘密情報スキャン: 検出なし（対象: {scope}の変更、{len(changed)} ファイル）")
    return 0


if __name__ == "__main__":
    # Windows でロケールが cp932 のとき、パイプやエージェントのツールで受けた日本語が化けないよう UTF-8 に固定する。
    # 差し替えではなく reconfigure を使い、終了時に書き出されないまま失われる出力が無いようにする。
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    sys.exit(main())
