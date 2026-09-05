<!-- markdownlint-disable-file MD041 -->
<!-- I want to review and to create summaries in Japanese. -->
<!-- for GitHub Copilot review rule -->
<!--
本テンプレートは、開発者およびAIエージェント（Claude Code / GitHub Copilot）が作成するPR用です。
本リポジトリでは外部からのPull Requestを受け付けていません（CONTRIBUTING.md 参照）。
不具合の報告・機能の要望・質問は Issue へお願いします。
-->

## 概要

<!-- 変更内容の簡潔な説明 -->

## 変更内容

<!-- 箇条書きで主な変更点を列挙。BACKLOG.mdの対象タスクがある場合はidを記載 -->

## 変更理由・背景

<!-- なぜこの変更が必要か -->

## テスト方法

<!-- 動作確認手順。実機確認を行った場合は機種とOSバージョンも記載 -->

## 関連事項

<!-- 関連するIssue番号、参考リンクなど -->

## チェックリスト

- [ ] 品質ゲートをローカルで実行し、すべて成功した
      （`./gradlew ktlintCheck detekt lintDebug testDebugUnitTest test assembleDebug`）
- [ ] Markdownを変更した場合、`npx markdownlint-cli2 "**/*.md"` が成功した
- [ ] 資格情報（apikey / secretKey / uuid）や個人情報が差分へ混入していないことを確認した
- [ ] 利用者に影響する変更の場合、`docs/RELEASE_NOTES.md` を更新した
- [ ] コード修正を伴う場合、`docs/records/managed/EXECUTE.md` を更新した
