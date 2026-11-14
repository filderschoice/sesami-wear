"""記録ファイルのYAML検証（品質ゲート）。

`docs/records/managed/BACKLOG.md` と `docs/records/managed/EXECUTE.md` の
`COPILOT_RECORDS:BEGIN` 〜 `COPILOT_RECORDS:END` の間を抽出し、YAMLとして読み込めることを
確認する（手順の正本は docs/records/spec/FORMAT.md「YAMLとしての体裁」）。
1件でも書式が壊れるとファイル全体がパースできなくなるため、記録ファイルを更新したら実行する。

あわせて、FORMAT.md が定める体裁のうち機械的に確認できるものを検査する。

- 改行コードがLFであること（PowerShellでの一括置換によるCRLF化を検出する）
- BACKLOG.md の必須キーが揃っていること、`区分`・`優先度`・`状態` が許容値であること
  （`区分` の許容値は FORMAT.md「キーの補足」の表から読み取る。配布先が表へ値を足しても本スクリプトを直さずに済む。
  表を読み取れない・許容値が0件のときは検査を飛ばさずエラーにする）
- BACKLOG.md の `id` が重複していないこと
- BACKLOG.md の `依存` が配列で、BACKLOG.md に存在する `id` だけを指していること
  （完了項目を削除したときに、他タスクの `依存` に削除済みの `id` が残る事故を検出する）
- EXECUTE.md の必須キー（`date`・`summary`・`details` の `変更内容`・`変更ファイル`・`検証コマンド`・`検証結果`）が
  揃っていること、`date` が `YYYY-MM-DD HH:mm` 形式であること、`変更ファイル` と（ある場合の）`関連ID` が配列であること

使い方:

    python scripts/validate-records.py

終了コードは、すべて成功で0、いずれかが失敗で1（記録ファイルが無い・読み込めない場合も、トレースバックではなく
該当ファイルを示すエラーで1）。PyYAML が必要（`pip install pyyaml`）。記録ファイルのパスは作業ディレクトリからの
相対パスのため、リポジトリのルートで実行する。
出力は UTF-8 に固定している。PowerShell でパイプ・変数へ受ける場合は、git の出力と同じく
`[Console]::OutputEncoding = [Text.Encoding]::UTF8` を設定してから実行する（PowerShell はコンソールの
コードページで外部コマンドの出力を解釈するため）。
"""

import io
import re
import sys

RECORD_FILES = [
    "docs/records/managed/BACKLOG.md",
    "docs/records/managed/EXECUTE.md",
]
BEGIN_MARKER = "<!-- COPILOT_RECORDS:BEGIN -->"
END_MARKER = "<!-- COPILOT_RECORDS:END -->"
FENCE_PATTERN = re.compile(r"^```yaml\s*$|^```\s*$", re.M)

BACKLOG_REQUIRED_KEYS = ["id", "区分", "タスク内容", "優先度", "状態", "担当", "完了条件", "依存"]
BACKLOG_PRIORITIES = ["P1", "P2", "P3"]
BACKLOG_STATES = ["未着手", "進行中", "ブロック", "要確認", "完了"]

# `区分` の許容値の正本は FORMAT.md「キーの補足」の表（`| 区分 | 用途 |` の見出しに続く行の1列目）
FORMAT_PATH = "docs/records/spec/FORMAT.md"
CATEGORY_TABLE_HEADER = re.compile(r"^\s*\|\s*区分\s*\|\s*用途\s*\|\s*$")
CATEGORY_ROW = re.compile(r"^\s*\|\s*`([^`]+)`\s*\|")

EXECUTE_REQUIRED_KEYS = ["date", "summary", "details"]
EXECUTE_REQUIRED_DETAIL_KEYS = ["変更内容", "変更ファイル", "検証コマンド", "検証結果"]
EXECUTE_LIST_DETAIL_KEYS = ["変更ファイル", "関連ID"]
# 秒を含まない `YYYY-MM-DD HH:mm` は PyYAML が文字列として読むため、文字列で照合する
# （秒まで書くと datetime、日付だけだと date に変換され、ここで不合格になる）
EXECUTE_DATE_PATTERN = re.compile(r"\d{4}-\d{2}-\d{2} \d{2}:\d{2}")


def load_records(path: str, errors: list[str]) -> list | None:
    """マーカー内をYAMLとして読み込む。失敗した場合は errors へ追記して None を返す。"""
    try:
        with io.open(path, encoding="utf-8", newline="") as handle:
            raw = handle.read()
    except FileNotFoundError:
        errors.append(f"{path}: ファイルが無い（リポジトリのルートで実行しているか、記録ファイルを配置したかを確かめる）")
        return None
    except (OSError, UnicodeDecodeError) as exc:
        errors.append(f"{path}: 読み込めない（{type(exc).__name__}）")
        return None
    if "\r" in raw:
        errors.append(f"{path}: 改行コードがCRLFになっている（FORMAT.md の規定はLF）")
    if BEGIN_MARKER not in raw or END_MARKER not in raw:
        errors.append(f"{path}: {BEGIN_MARKER} / {END_MARKER} が見つからない")
        return None
    body = raw.split(BEGIN_MARKER)[1].split(END_MARKER)[0]
    try:
        import yaml
    except ImportError:
        errors.append("PyYAML が見つからない。pip install pyyaml を実行する")
        return None
    try:
        return yaml.safe_load(FENCE_PATTERN.sub("", body))
    except Exception as exc:  # noqa: BLE001 - YAMLの失敗理由をそのまま見せる
        errors.append(f"{path}: YAMLとして読み込めない - {exc}")
        return None


def load_categories(errors: list[str]) -> list[str] | None:
    """FORMAT.md「キーの補足」の表から `区分` の許容値を読み取る。

    FORMAT.md を読めない、または読めても表が見つからない・許容値が0件のときは errors へ追記して None を返す。
    表の見出しの書き換えなどで読み取りが壊れたときに、`区分` の検査を黙って飛ばしてゲートが通るのを防ぐため。
    """
    try:
        with io.open(FORMAT_PATH, encoding="utf-8") as handle:
            lines = handle.read().splitlines()
    except OSError:
        errors.append(f"{FORMAT_PATH}: 読み込めないため `区分` の許容値を確認できない")
        return None
    categories: list[str] = []
    in_table = False
    for line in lines:
        if not in_table:
            in_table = bool(CATEGORY_TABLE_HEADER.match(line))
            continue
        if not line.strip().startswith("|"):
            break
        match = CATEGORY_ROW.match(line)
        if match:
            categories.append(match.group(1))
    if not categories:
        errors.append(
            f"{FORMAT_PATH}: 「キーの補足」の `| 区分 | 用途 |` の表から `区分` の許容値を1件も読み取れない"
            "（表の見出しか行の書き方が変わっていないか確かめる）"
        )
        return None
    return categories


def check_backlog_keys(records: list, categories: list[str] | None, errors: list[str]) -> None:
    """BACKLOG.md の必須キーと、許容値が決まっているキーの値を確認する。"""
    for record in records:
        task_id = record.get("id", "(idなし)")
        for key in BACKLOG_REQUIRED_KEYS:
            if key not in record:
                errors.append(f"BACKLOG.md {task_id}: 必須キー `{key}` が無い")
        priority = record.get("優先度")
        if priority is not None and priority not in BACKLOG_PRIORITIES:
            errors.append(f"BACKLOG.md {task_id}: 優先度 `{priority}` は許容値ではない")
        state = record.get("状態")
        if state is not None and state not in BACKLOG_STATES:
            errors.append(f"BACKLOG.md {task_id}: 状態 `{state}` は許容値ではない")
        category = record.get("区分")
        if categories is not None and category is not None and category not in categories:
            errors.append(
                f"BACKLOG.md {task_id}: 区分 `{category}` は許容値ではない（FORMAT.md「キーの補足」の表から選ぶ）"
            )


def check_backlog_unique_ids(records: list, errors: list[str]) -> None:
    """BACKLOG.md の `id` が重複していないことを確認する。"""
    seen: set = set()
    for record in records:
        task_id = record.get("id")
        if task_id is None:
            continue
        if task_id in seen:
            errors.append(f"BACKLOG.md {task_id}: id が重複している（使用済みの番号と衝突しない番号を採る）")
        seen.add(task_id)


def check_backlog_dependencies(records: list, errors: list[str]) -> None:
    """BACKLOG.md の `依存` が配列で、存在する `id` だけを指していることを確認する。"""
    ids = {record.get("id") for record in records}
    for record in records:
        task_id = record.get("id", "(idなし)")
        depends = record.get("依存")
        if depends is None:
            continue
        if not isinstance(depends, list):
            errors.append(f"BACKLOG.md {task_id}: 依存 は配列で書く（依存が無い場合は []）")
            continue
        for dep in depends:
            if dep not in ids:
                errors.append(
                    f"BACKLOG.md {task_id}: 依存 `{dep}` は BACKLOG.md に存在しない"
                    "（完了して削除した id は依存から外し、空になったら [] にする）"
                )


def check_execute_records(records: list, errors: list[str]) -> None:
    """EXECUTE.md の必須キー、`date` の形式、配列で書くキーの型を確認する。"""
    for index, record in enumerate(records, start=1):
        if not isinstance(record, dict):
            errors.append(f"EXECUTE.md {index}件目: キーと値の組で書かれていない")
            continue
        label = f"EXECUTE.md {index}件目（date {record.get('date', 'なし')}）"
        for key in EXECUTE_REQUIRED_KEYS:
            if key not in record:
                errors.append(f"{label}: 必須キー `{key}` が無い")
        date = record.get("date")
        if date is not None and not (isinstance(date, str) and EXECUTE_DATE_PATTERN.fullmatch(date)):
            errors.append(f"{label}: date は `YYYY-MM-DD HH:mm` 形式で書く")
        details = record.get("details")
        if details is None:
            continue
        if not isinstance(details, dict):
            errors.append(f"{label}: details はキーと値の組で書く")
            continue
        for key in EXECUTE_REQUIRED_DETAIL_KEYS:
            if key not in details:
                errors.append(f"{label}: 必須キー `details.{key}` が無い")
        for key in EXECUTE_LIST_DETAIL_KEYS:
            if key in details and not isinstance(details[key], list):
                errors.append(f"{label}: details.{key} は配列で書く")


def main() -> int:
    errors: list[str] = []
    for path in RECORD_FILES:
        records = load_records(path, errors)
        if records is None:
            continue
        print(f"{path}: OK ({len(records)} records)")
        if path.endswith("BACKLOG.md"):
            check_backlog_keys(records, load_categories(errors), errors)
            check_backlog_unique_ids(records, errors)
            check_backlog_dependencies(records, errors)
        if path.endswith("EXECUTE.md"):
            check_execute_records(records, errors)
    if errors:
        print("")
        for error in errors:
            print(f"NG: {error}")
        return 1
    return 0


if __name__ == "__main__":
    # Windows でロケールが cp932 のとき、パイプやエージェントのツールで受けた日本語が化けないよう UTF-8 に固定する。
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    sys.exit(main())
