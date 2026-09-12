---
name: guardrail-sync-local
description: ガードレール・エージェント指示ルールを配布元リポジトリから取り込むときの、本リポジトリ側の事情をまとめた配布先プロファイル。配布元の所在、共通ファイルの三分類（そのままコピー／固有改変をマージ／配布元を採らない）と各判断の理由、取り込み後に必ず確認する点、常時読み込みサイズのベースラインを持つ。「ガードレールを取り込んで」「ルールを配布元へ追従させて」「CLAUDE.mdを最適化して」「コンテキストサイズを減らして」等の依頼で、汎用手順の guardrail-rules-sync とあわせて読む（手順そのものは同スキル、記録ファイルの書き方は guardrail-records-update が正本）。
---

# ガードレール取り込みの配布先プロファイル（Sesami Wear）

汎用手順は Skill `guardrail-rules-sync` が正本。**本スキルは、その手順のうち「配布先ごとに決める」と
されている部分だけ**を本リポジトリの実情で埋める。両方を読んでから着手する。

## 配布元

| 項目 | 値 |
| --- | --- |
| 配布元リポジトリ | `C:\Dev\repo\copilot-rules` |
| 追従判断の根拠 | 同リポジトリの `CHANGELOG.md`（バージョン節に意図と計測値がある） |
| 取り込み実績 | v0.23.0（2026-09-12。「共通規約1ファイル + エージェント固有差分」構成へ再編） |

配布元は `scripts/package-rules.*` と `dist/` を持つが、本リポジトリは配布**先**なので取り込まない。

## ファイルの三分類（本リポジトリでの確定値）

`guardrail-rules-sync`「ファイルの三分類」を本リポジトリへ当てはめた結果。**分類を間違えると
固有の記述が消えるか、配布元との差分が毎回残る。**

### そのままコピー（`cp` で丸ごと置換）

- `rules/guardrails-unified.v1.md`
- `docs/guidelines/RULE.md` / `README.md` / `ADOPTION.md`
- `docs/records/README.md`
- `templates/app-guardrail-template.yaml` / `model-risk-register-template.csv`
- `.github/instructions/pr.instructions.md`

### 固有改変をマージ（配布元の構造を採り、固有の内容は残す）

| ファイル | 残す固有の内容 |
| --- | --- |
| `CLAUDE.md` | アーキテクチャ概要、処理フロー、参照先マップ、`--tests` と実機インストールのコマンド、品質ゲート定義 |
| `.github/copilot-instructions.md` | 参照ドキュメント表の本リポジトリ行、実資格情報を使わない旨のセキュリティ補足、記録対象の `docs/RELEASE_NOTES.md` と利用者向けドキュメント |
| `CONTRIBUTING.md` | **冒頭の外部コントリビューション方針（Issueは受付・PRは非受付）、レビュー要件（メンテナー1名体制）、品質ゲート、ドキュメント管理** |
| `docs/records/spec/FORMAT.md` | `BACKLOG.md` の記述例（Pixel Watch実機・ハプティクスの文面。配布元は汎用文面へ置換済み） |

**`CONTRIBUTING.md` に注意。** 汎用手順では「そのままコピー」に分類されているが、本リポジトリの
`CONTRIBUTING.md` は配布元の約2倍あり、前半すべてが外部の方向けの受け付け方針で占められている。
丸ごと置換すると公開リポジトリの窓口説明が消える。必ずマージする。

### 配布元を採らない（本リポジトリ版を維持する）

| ファイル | 理由 |
| --- | --- |
| `.markdownlint-cli2.yaml` | `**/*.local.md` の除外と `MD024: siblings_only` を追加済み（`docs/RELEASE_NOTES.md` がバージョンごとに同名見出しを繰り返すため） |
| `.github/PULL_REQUEST_TEMPLATE.md` | 品質ゲート・資格情報混入確認のチェックリストを持つ独自版 |
| `.github/CODEOWNERS` | メンテナー1名体制（配布元は `@your-org/...` のプレースホルダ） |
| `.claude/settings.json` | `Edit(core/**)` / `Edit(mobile/**)` / `Edit(wear/**)` を追加済み |

## 取り込み後に必ず確認すること

汎用手順に加えて、本リポジトリで実際に問題が出た箇所。

### 1. 新しくなった `FORMAT.md` の規約で既存の記録を検証する

配布元が記述仕様へ**新しい体裁ルールを追加している**場合、既存レコードがその時点で規約違反になる。
v0.23.0 の「YAMLとしての体裁」取り込み時、`EXECUTE.md` の既存3レコードが
`検証結果: 成功 - BUILD SUCCESSFUL（132 actionable tasks: 13 executed, ...）` の
「半角コロン＋半角空白」により `safe_load` できない状態だったことが判明した（取り込み前から壊れていた）。

````bash
python - <<'PYEOF'
import re, io, yaml
for p in ['docs/records/managed/BACKLOG.md','docs/records/managed/EXECUTE.md']:
    s=io.open(p,encoding='utf-8').read()
    inner=s.split('COPILOT_RECORDS:BEGIN -->')[1].split('<!-- COPILOT_RECORDS:END')[0]
    n=sum(len(yaml.safe_load(b) or []) for b in re.findall(r'```yaml\n(.*?)```', inner, re.S))
    print(p, 'OK records=%d' % n)
PYEOF
````

失敗したら `FORMAT.md` の推奨どおりブロックスカラー（`>-`）へ直す。**記録内容は書き換えない**
（既存ログの削除・改変は禁止。体裁だけを変える）。修正後に `file docs/records/managed/*.md` で
CRLF化していないことも確認する。

### 2. 品質ゲート定義が2箇所で食い違っていないか

本リポジトリはコマンドの正本を `CLAUDE.md`「本リポジトリの品質ゲート定義」に置き、
`CONTRIBUTING.md`「品質ゲート」にも実行用のコードブロックを持つ。**片方だけ更新される事故が実際に
起きていた**（`CLAUDE.md` 側に `npx markdownlint-cli2` と `test` タスクが欠けていた）。

```bash
grep -n "gradlew\|markdownlint" CLAUDE.md CONTRIBUTING.md | grep -v "^CHANGELOG"
```

現在の品質ゲート（6項目）:

```bash
./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug
npx markdownlint-cli2 "**/*.md"
```

定義を変えたら、**それを説明している派生記述**も追う。段階A/Bの区分を廃止したとき、
`README.md` の「上記5コマンドが本リポジトリの品質ゲート（段階B）です」が取り残された。

```bash
grep -rn "段階B\|品質ゲート" --include="*.md" . | grep -v node_modules | grep -v CHANGELOG | grep -v records/managed
```

### 3. markdownlint と リンク検査はドット配下を拾わない

`npx markdownlint-cli2 "**/*.md"` は `.claude/` `.github/` 配下を**検査しない**。
汎用手順のリンク検査スクリプトの `glob('**/*.md')` も同様。別途指定する。

```bash
npx markdownlint-cli2 "**/*.md"
npx markdownlint-cli2 ".claude/skills/**/*.md" ".github/**/*.md"
```

## 本リポジトリ固有の読み替え

- **人手検証の3類型**（自動品質ゲートから除外し `区分: 人手検証` で `BACKLOG.md` へ残す）:
  Pixel Watch実機・Sesame実機を伴う動作確認 / 実資格情報（apikey・secretKey・uuid）を用いる疎通確認 /
  Google Play Console上の操作。AES-CMACは RFC 4493 の公開テストベクタとダミー鍵で単体テストする。
- **記録ファイルのヘッダコメント**（`<!-- Copilot専用... -->`）は `COPILOT_RECORDS` マーカー**外**の
  ため触らない。配布元でも未対応（表記統一は配布元の課題）。
- **`CLAUDE.md` の節名参照**は `README.md`・`CONTRIBUTING.md`・`BACKLOG.md` から張られている。
  節を改名・移設したら必ず追う（`grep -rn 'CLAUDE\.md[「]' --include="*.md" .`）。

## 常時読み込みサイズのベースライン

2026-09-12 の最適化完了時点の実測値。次回の追従で悪化していないかの基準に使う
（数値そのものは古くなるので、比較のたびに測り直す）。

| ファイル | bytes | 備考 |
| --- | --- | --- |
| `CLAUDE.md` | 17,784 | 固有情報を持つので配布先ごとに変わる |
| `.github/copilot-instructions.md` | 13,152 | 共通規約の実体 |
| `rules/guardrails-unified.v1.md` | 11,083 | 配布元の正本。**圧縮しない**（フォークすると同期が破綻する） |
| 合計 | **42,019** | 再編前は 46,225（`CLAUDE.md` 34,842 + guardrails 11,383） |

guardrails が合計の26%を占めるが、本リポジトリでは削減対象にしない。削るなら配布元側で
「適用可否を宣言できる仕組み」を入れるのが筋（`guardrail-rules-sync`「触ってはいけないもの」参照）。

## 更新履歴

- 2026-09-12: 配布元 v0.23.0 の取り込みと常時読み込みの最適化（PR #25）を経て新規作成。
  三分類の確定値、`CONTRIBUTING.md` を「そのままコピー」に分類してはいけない理由、
  取り込み後に実際に問題が出た3点、ベースライン値を収録した。
