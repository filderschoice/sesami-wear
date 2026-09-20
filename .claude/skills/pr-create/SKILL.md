---
name: pr-create
description: 本リポジトリでPull Requestを作成・更新するときのClaude Code側の実行手順。規範の正本は`.github/instructions/pr.instructions.md`と`.github/PULL_REQUEST_TEMPLATE.md`で、本スキルはそれをghコマンドで確実に反映する手順だけを扱う。作業ブランチが未pushならPRを作れないこと（pushはユーザーが実行する）、PowerShellでバッククォートを含む本文を`--body`へ直接渡すと引数分割が壊れる事故と回避策（一時ファイル＋`--body-file`）、実行していない品質ゲートをチェックリストで成功扱いにしないことを含む。「PRを作成して」「PRの説明文を作って」「テンプレに沿ってPRを直して」「プルリクを出して」等の依頼で参照する。
---

# PR作成手順（Claude Code 用）

## 0. 規範の正本を先に読む（MUST）

| 読む順 | ファイル | 何が書いてあるか |
| --- | --- | --- |
| 1 | [`.github/instructions/pr.instructions.md`](../../../.github/instructions/pr.instructions.md) | 言語要件、本文の型、タイトル規約、チェックリストの扱い、品質ゲート対応表、PR作成の前提 |
| 2 | [`.github/PULL_REQUEST_TEMPLATE.md`](../../../.github/PULL_REQUEST_TEMPLATE.md) | **実際に使う本文の型**（チェックリスト節はこちらにしかない） |

**Claude Code には `.github/instructions/` を自動適用する機構が無いため、毎回この2ファイルを読んでから
本文を組み立てます**（`CLAUDE.md`「共通規約に対する Claude Code 固有の差分」）。GitHub Copilot 側の
入口は `.github/prompts/pr-create.prompt.md` で、規範は上記と共通です。本スキルは規範を複製せず、
`gh` コマンドで確実に反映する手順だけを扱います。

## 1. 着手できる状態かを確認する（MUST）

```bash
git branch --show-current          # 規定ブランチ（既定 main）でないこと
git status --short                 # 未コミットの変更が残っていないこと
git log origin/HEAD..HEAD --oneline  # ローカルにしか無いコミットが無いこと
gh --version && git remote -v
```

- **作業ブランチの `git push` はユーザーが実行します。** エージェントは push しません
  （共通規約「ブランチ・コミット管理」）。未 push のブランチにはPRを作成できないため、
  push が済んでいなければ**本文案とpushコマンド例を提示して待ちます**。ここで先へ進もうとして
  `git push` を代行しないでください。
- PRの作成・更新はGitHub上へ公開される後戻りしにくい操作です。ユーザーから明示的に指示された
  場合にのみ実行します（`rules/guardrails-unified.v1.md` 3.2）。自律ループ実行モードでは行いません（12.2）。

## 2. 差分を読む（MUST）

```bash
git log main..HEAD --oneline
git diff main...HEAD --stat
git diff main...HEAD
```

読まずに要約すると、実際の変更とズレた本文になります。

## 3. 品質ゲートを実行してから記載する（MUST）

変更の種類ごとの対応表は pr.instructions.md「チェックリストの扱い」が正本です。
**実行して確認できた項目だけをチェックし、対象外の項目は理由を添えて未チェックのまま残します。**

## 4. `gh pr create` / `gh pr edit` で反映する（MUST）

**本文にバッククォート（`` ` ``）やコードブロックを含む場合、PowerShellの `--body "..."` へ直接
埋め込まないでください。** PowerShellはバッククォートをエスケープ文字として解釈するため、
文字列が意図しない位置で分割され `accepts at most 1 arg(s), received 2` で失敗します（実際に踏んだ事故）。

本文をMarkdownファイルへ書き出し、`--body-file` で渡します。

```bash
gh pr create --base main --title "<日本語タイトル>" --body-file <一時ファイル>
gh pr edit <PR番号> --body-file <一時ファイル>   # 既存PRの本文を直す場合
```

- 一時ファイルは**リポジトリの追跡対象外**へ作成します。Claude Code ではシステムプロンプトが示す
  スクラッチパッドディレクトリを使います（無ければOSのTEMPディレクトリ）。リポジトリ配下へ置くと
  次のPRの差分へ混入します。
- タイトルに絵文字は付けません（pr.instructions.md「本文の型」）。
- PR説明文末尾の生成元表記（`🤖 Generated with ...`）は、セッションのハーネスが指示している場合に
  限り付けます。リポジトリの規約としては要求していません。

## 5. 反映を確認して後始末する（MUST）

```bash
gh pr view <PR番号> --json url,title,body -q .url
```

- 出力されたURLをユーザーへ提示します。
- 一時ファイルを削除します。

## よくある事故

- **テンプレートを読まずに `pr.instructions.md` の節名だけで本文を作り、チェックリスト節が丸ごと抜ける。**
  → 第0節の2ファイルを必ず読む。
- **チェックリストを全部 `[x]` にする。** 実行していないゲート（コード変更が無いのに `assembleDebug` など）
  まで成功扱いにすると、レビュアーへ誤った保証を与える。→ 対象外の理由を書いて未チェックで残す。
- **未 push のブランチでPRを作ろうとして失敗する／その場で push を代行してしまう。**
  → push はユーザーの作業。本文案を提示して待つ（第1節）。
- **PowerShellでバッククォートを含む本文を `--body` へ直接渡して失敗する。** → `--body-file` を使う（第4節）。
- **一時ファイルをリポジトリ配下へ作り、消し忘れて次の差分へ混入する。** → スクラッチパッドへ作り、削除する。
