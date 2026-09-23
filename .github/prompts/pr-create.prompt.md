---
mode: agent
description: "テンプレートに沿ったPR説明文を組み立て、Pull Requestを作成・更新する"
---

# PR作成（GitHub Copilot 用）

規範の正本は [`.github/instructions/pr.instructions.md`](../instructions/pr.instructions.md) と
[`.github/PULL_REQUEST_TEMPLATE.md`](../PULL_REQUEST_TEMPLATE.md) です。**本ファイルは入口であり、
規範を複製しません。** 着手前に両方を読んでから、以下の順で進めてください。

1. **前提を確認する。** `git branch --show-current` が規定ブランチ（既定 `main`）でないこと、
   作業ブランチがリモートへ push 済みであることを確認する。**push はユーザーが行う**ため、
   未 push なら本文だけを提示し、push を依頼して待つ。
2. **差分を読む。** `git log main..HEAD --oneline` と `git diff main...HEAD` を確認してから本文を書く。
3. **品質ゲートを実行する。** 実行するゲートは pr.instructions.md「チェックリストの扱い」に従う。
   実行していないゲートはチェックしない。
4. **本文を組み立てる。** `.github/PULL_REQUEST_TEMPLATE.md` の6節構成をそのまま使い、
   チェックリストはテンプレート本体の文言を維持する。対象外の項目は理由を添えて未チェックで残す。
5. **反映する。** VS Code の Pull Request ビューから作成する場合は、生成した本文を貼り付ける。
   ターミナルから行う場合は `gh pr create --base main --title "<タイトル>" --body-file <一時ファイル>`
   を使う（本文を `--body` へ直接渡さない。理由は下記）。
6. **URLをユーザーへ提示し、一時ファイルを削除する。**

## Copilot 固有の注意（MUST）

- **`git add` / `git commit` / `git push` は実行しない。** Copilot はいかなる場合もこれらを実行せず、
  必要時はコマンド例のみ提示します（`.github/copilot-instructions.md`「Copilot 固有の差分」）。
- **PRの作成・更新はユーザーから明示的に指示された場合にのみ行う。** GitHub上へ公開される
  後戻りしにくい操作です（`rules/guardrails-unified.v1.md` 3.2）。指示が無ければ本文の提示までで止めます。
- **本文にバッククォートやコードブロックを含む場合、`--body` へ直接埋め込まない。**
  PowerShell はバッククォートをエスケープ文字として解釈するため、文字列が意図しない位置で分割され
  `accepts at most 1 arg(s), received 2` で失敗します。本文をMarkdownファイルへ書き出し
  `--body-file` で渡してください。一時ファイルはリポジトリの追跡対象外（OSのTEMPディレクトリ等）へ
  作成し、反映を確認したら削除します。
- **本ファイルが `/pr-create` として一覧に出ない場合**は、VS Code 側でプロンプトファイルが
  無効になっている可能性があります（設定名・既定値はVS Codeのバージョンに依存するため未確認）。
  その場合は本ファイルをチャットへ添付するか、pr.instructions.md を直接参照してください。
