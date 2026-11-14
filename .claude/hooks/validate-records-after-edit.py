"""PostToolUse フック: 記録ファイルを編集した直後に `scripts/validate-records.py` を実行する。

対象は、プロジェクトの `docs/records/managed/` 配下の BACKLOG.md・EXECUTE.md・DESIGN.md を Edit・Write・MultiEdit で
編集したときだけ。それ以外の編集・不正な入力では何もせず終了コード0で終わる（毎回の編集に重い処理を足さないため）。

終了コード（Claude Code のフックの取り決めに合わせる）:

- 0: 対象外、または検証に成功
- 2: 検証に失敗。validate-records.py の出力を標準エラーへ出す。PostToolUse では編集は取り消されないが、標準エラーが
  Claude へ伝わり、YAML の破損や `依存` の不整合をその場で直させる。複数回の編集で整える途中（項目を消した直後で、
  他の `依存` をまだ直していない等）でも失敗として伝わるため、一連の編集を終えた時点の結果で判断させる
- 1: 検証を実行できない（`scripts/validate-records.py` が無い、PyYAML が無い、起動に失敗した）。Claude Code は
  0・2 以外を「ブロックしないエラー」として扱うため、編集作業そのものは妨げない

標準ライブラリのみで動く（検証自体は PyYAML が必要）。入力の JSON はこのスクリプトの中でだけ解釈し、シェルの
コマンドへ連結しない。登録方法と起動コマンドの理由は docs/guidelines/ADOPTION.md と DESIGN.md を参照する。
"""

import importlib.util
import json
import os
import subprocess
import sys

TARGET_TOOLS = ("Edit", "Write", "MultiEdit")
TARGET_FILES = ("BACKLOG.md", "EXECUTE.md", "DESIGN.md")
TARGET_DIR = os.path.join("docs", "records", "managed")
VALIDATOR = "scripts/validate-records.py"
TIMEOUT_SECONDS = 60
PREFIX = "[validate-records-after-edit]"


def project_root() -> str:
    # CLAUDE_PROJECT_DIR が無いときは、このファイルの位置（<root>/.claude/hooks/）から求める
    root = os.environ.get("CLAUDE_PROJECT_DIR")
    if root:
        return os.path.abspath(root)
    return os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def is_target(file_path: str, root: str) -> bool:
    path = file_path if os.path.isabs(file_path) else os.path.join(root, file_path)
    normalized = os.path.normcase(os.path.normpath(os.path.abspath(path)))
    for name in TARGET_FILES:
        target = os.path.normcase(os.path.normpath(os.path.join(root, TARGET_DIR, name)))
        if normalized == target:
            return True
    return False


def write_error(message: str) -> None:
    sys.stderr.buffer.write((message + "\n").encode("utf-8"))
    sys.stderr.flush()


def main() -> int:
    try:
        data = json.loads(sys.stdin.buffer.read().decode("utf-8") or "{}")
    except ValueError:
        return 0
    if not isinstance(data, dict) or data.get("tool_name") not in TARGET_TOOLS:
        return 0
    tool_input = data.get("tool_input")
    file_path = tool_input.get("file_path") if isinstance(tool_input, dict) else None
    if not isinstance(file_path, str) or not file_path:
        return 0
    root = project_root()
    if not is_target(file_path, root):
        return 0

    validator = os.path.join(root, *VALIDATOR.split("/"))
    if not os.path.isfile(validator):
        write_error(f"{PREFIX} {VALIDATOR} が無いため検証を省略した")
        return 1
    if importlib.util.find_spec("yaml") is None:
        write_error(f"{PREFIX} PyYAML が無いため検証を省略した（pip install pyyaml）")
        return 1
    try:
        # 引数は固定の値だけで組み立て、シェルを介さずに起動する
        result = subprocess.run(
            [sys.executable, validator],
            cwd=root,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=TIMEOUT_SECONDS,
            check=False,
        )
    except (OSError, subprocess.SubprocessError) as error:
        write_error(f"{PREFIX} {VALIDATOR} を実行できなかった（{type(error).__name__}）")
        return 1
    if result.returncode == 0:
        return 0
    output = result.stdout.decode("utf-8", errors="replace").strip()
    write_error(
        f"{PREFIX} 記録ファイルの検証に失敗した（docs/records/spec/FORMAT.md を参照して直すこと）\n{output}"
    )
    return 2


if __name__ == "__main__":
    sys.exit(main())
