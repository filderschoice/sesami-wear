# Copilot / Claude Code ルール導入・運用ガイド

本リポジトリで管理している Copilot・Claude Code 用のプロセスルールとガードレールを、新規リポジトリと
既存リポジトリへ適用して運用するための実務手順です。

- 導入判断 → 導入実施 → 日常運用の順に読みます。
- すぐ着手する場合は「2. クイックスタート」から開始します。
- 既存リポジトリへの移行は「4. 既存リポジトリへの適用手順」を先に読みます。

## 1. 導入資産一覧

本ガイドで参照するファイルの正本一覧です。以降の手順・チェックリストはすべてこの表を参照します。

| パス | 役割 | 区分 |
| --- | --- | --- |
| `rules/guardrails-unified.v1.md` | セキュリティ・プライバシー・ガバナンス統制の正本 | 必須 |
| `docs/guidelines/RULE.md` | 汎用フレームワーク（基本原則・出力要件・完了条件） | 必須 |
| `docs/guidelines/ADOPTION.md` | 本ガイド（導入・移行・日常運用） | 必須 |
| `CONTRIBUTING.md` | 規定ブランチ、ブランチ・コミット規約、レビュー要件、指示ファイルの構成規約 | 必須 |
| `CHANGELOG.md` | 変更履歴（配布元の追従判断にも使用） | 必須 |
| `docs/records/README.md` | records 配下の運用ルール | 必須 |
| `docs/records/spec/FORMAT.md` | 記録ファイルの記述仕様（唯一の参照元） | 必須 |
| `docs/records/managed/DESIGN.md` | 再実装用プロンプト設計書（最新版） | 必須 |
| `docs/records/managed/EXECUTE.md` | コード修正を伴う変更の実施記録 | 必須 |
| `docs/records/managed/BACKLOG.md` | 未対応事項・課題・次ステップ | 必須 |
| `.github/copilot-instructions.md` | 全エージェント共通の実行ルールの正本（Copilot 固有差分を末尾に含む） | 必須 |
| `.github/instructions/pr.instructions.md` | PR説明文・コードレビューの言語と構成（両エージェント共通の正本） | 必須 |
| `.github/CODEOWNERS` | 変更統制（`@your-org/...` を実在チームへ置換） | 必須 |
| `.github/PULL_REQUEST_TEMPLATE.md` | PRテンプレート | 必須 |
| `templates/app-guardrail-template.yaml` | アプリ別ガードレール設定（`replace-me` を解消） | 必須 |
| `templates/model-risk-register-template.csv` | モデルリスク登録簿（初期リスク2件以上） | 必須 |
| `.vscode/settings.json` | `pr.instructions.md` を Copilot Chat から参照させる設定 | Copilot のみ |
| `CLAUDE.md` | Claude Code 固有の差分と追加規約（リポジトリルート直下） | Claude Code のみ |
| `.claude/settings.json` | 記録ファイル編集を権限プロンプトなしで許可する設定 | Claude Code のみ・任意 |
| `.claude/skills/docs-consistency-review/SKILL.md` | ドキュメント整合性レビューの汎用スキル | Claude Code のみ・任意 |
| `.claude/skills/autonomous-loop/SKILL.md` | 自律ループ実行モードの実行手順 | Claude Code のみ・モード採用時は必須 |

補足:

- `.github/copilot-instructions.md` は**全エージェント共通の実行ルールの実体**です。Claude Code のみを
  利用する配布先でも必須で、`CLAUDE.md` が `@import` で読み込みます。ファイル名は GitHub が自動読み込み
  するパスとして規約で固定されているため変更できません。役割分担は `CONTRIBUTING.md`
  「エージェント指示ファイルの構成規約」を参照してください。
- `.vscode/settings.json` をリポジトリに含めない運用の場合、各利用者の設定で同等の指定が必要です。
- `.claude/settings.json` を設定しない場合、記録更新のたびに権限確認が入ります。採否は配布先の判断です。
- `.claude/skills/` のうち配布されるのは技術スタックに依存しない汎用スキルのみです。配布先固有の作業手順を
  記述したスキルは**配布対象外**であり、導入先で同じ形式により新規作成します。

### 1.1 `CLAUDE.md` の `@import` について

`CLAUDE.md` は `@import` 記法で指定したファイルをセッション開始時に自動読み込みします。リポジトリルート
からの相対パスが崩れていないことを確認してください。

自動読み込みはセッションのコンテキストを消費し続けるため、対象は最小限にします。配布元では次の2ファイルのみを
`@import` し、`CONTRIBUTING.md` / `docs/guidelines/RULE.md` / `docs/records/spec/FORMAT.md` などは必要に
なった時点で読む構成にしています。

- `rules/guardrails-unified.v1.md`（セキュリティ・プライバシー・統制の正本）
- `.github/copilot-instructions.md`（全エージェント共通の実行ルールの正本）

配布先で `CLAUDE.md` へ内容を具体化した場合も、同じ方針で `@import` 対象を絞ってください
（変更した場合はその判断を `CHANGELOG.md` へ記録します）。

### 1.2 自律ループ実行モードの採否

Claude Code 固有の運用モードです。採用する場合は次の4点がすべて必要です。

| 資産 | 役割 |
| --- | --- |
| `rules/guardrails-unified.v1.md` セクション12 | 統制要件（許可・禁止操作、停止条件、秘密情報の取り扱い）。常時読み込み |
| `CONTRIBUTING.md`「自律ループ実行モードのブランチ・コミット規約」 | ブランチとコミットの運用 |
| `CLAUDE.md`「自律ループ実行モード（Loop Engineering）」 | 適用条件とスキルの起動方法。常時読み込み |
| `.claude/skills/autonomous-loop/SKILL.md` | 実行手順の正本。モード起動時のみ読み込み |

実行手順をスキルへ分離しているのは、本モードが明示指示時にしか使われないためです。統制要件は常時読み込み
されるため、スキルを読み込んでいない状態でも禁止事項は有効です。

採用しない場合は `CLAUDE.md` から該当節を削除し、`.claude/skills/autonomous-loop/` を配置せず、
`git add` / `git commit` を実行しない既定の運用へ統一してください。

## 2. クイックスタート

1. 規定ブランチから作業ブランチを作成する。
2. 「1. 導入資産一覧」の必須ファイルと、利用するエージェントの区分のファイルを配置する。
3. `.github/copilot-instructions.md` / `CLAUDE.md` の参照パスを現行構成に合わせる。
4. テンプレートのプレースホルダ（`replace-me` / `@your-org/...`）を埋める。
5. 初期リスク登録と受け入れ確認（リンク整合性、エージェント応答、PR 運用）を実施する。

## 3. 適用方式

- 推奨方式: 配布先リポジトリに「正本コピー」を配置し、差分を PR で管理する。
- バージョン管理: ルール本文はファイル名バージョン（例: `guardrails-unified.v1.md`）で管理する。
- 更新同期: 配布元（本リポジトリ）の `CHANGELOG.md` を参照して追従判断する。

## 4. 既存リポジトリへの適用手順

既存運用を壊さないため、必ず「追加適用」から始めます。作業は規定ブランチから切った作業ブランチで実施します。

### 4.1 ギャップ分析

1. 現行の AI 利用ルール、セキュリティルール、PR 運用を棚卸しする。
2. 差分を「追加（現行に存在しない統制）」「競合（既存ルールと矛盾）」「置換（統合したい重複ルール）」へ分類する。
3. 競合項目は「優先ルール」「移行期限」「責任者」を決める。

### 4.2 段階導入

1. Phase 1（参照導入）: `rules/guardrails-unified.v1.md` と `docs/guidelines/RULE.md` を導入し、既存ルールから参照する。
2. Phase 2（運用導入）: エージェント指示ファイルを導入し、「1. 導入資産一覧」の必須ファイルの存在と参照整合性を確認する。
3. Phase 3（統合完了）: 重複ルールを整理し、正本を 1 か所に寄せる。

### 4.3 互換性管理

- 既存のレビュー手順を壊さないよう、まず「追加適用」で開始する。
- 破壊的変更がある場合は、旧ルール併用期間を設定する。
- CI や監査ログ要件と衝突する場合は、`docs/records/managed/DESIGN.md` へ判断理由を反映する。

## 5. 新規リポジトリへの適用手順

作業は規定ブランチから切った作業ブランチで実施します。

1. 「1. 導入資産一覧」の必須ファイルと、利用するエージェントの区分のファイルを配置する。
2. エージェント指示ファイルの参照パスが実フォルダ構成と一致し、参照先がすべて存在することを確認する。
3. `docs/records/README.md` と `docs/records/spec/FORMAT.md` に基づき、records をエージェント自動更新前提で運用する。
4. `templates/app-guardrail-template.yaml` の `replace-me` をすべて埋める。
5. `templates/model-risk-register-template.csv` に初期リスクを最低 2 件以上登録する。
6. `CONTRIBUTING.md` に規定ブランチとレビュー責任者を明記する。
7. `.github/CODEOWNERS` と `.github/PULL_REQUEST_TEMPLATE.md` を組織体制に合わせて調整する。

### 5.1 受け入れ確認

1. ルール参照整合性を確認する（リンク切れ・パス誤りがないこと）。
2. 代表的なタスクで、禁止事項と出力要件が期待通りに働くことを確認する。
3. PR ベース運用（規定ブランチ直コミット禁止、レビュー必須）が有効であることを確認する。

## 6. 日常運用ガイド

### 6.1 変更管理

1. ルール変更は必ず作業ブランチで実施する。
2. 変更時は `CHANGELOG.md` を更新する。
3. 影響判断はエージェント指示経由で `docs/records/managed/DESIGN.md` へ反映する。
4. コード修正を伴う場合のみ、エージェント指示経由で `docs/records/managed/EXECUTE.md` を更新する。
5. 未対応事項はエージェント指示経由で `docs/records/managed/BACKLOG.md` で管理する。
6. records の記述仕様変更は `docs/records/spec/FORMAT.md` を正本として更新し、
   `docs/records/managed/*.md` 本体には定義外の構造を追加しない。

### 6.2 records 自動更新運用

- `docs/records/managed/` 配下の3ファイルはエージェントによる自動更新専用として扱う。
- 各ファイルは `COPILOT_RECORDS:BEGIN` と `COPILOT_RECORDS:END` の間のみ更新する
  （マーカー名は導入時の命名を継続利用しており、全エージェント共通です）。
- 記述ルール・記述例は `docs/records/spec/FORMAT.md` を唯一の参照元とする。
- ユーザやレビューアは records 本体を直接編集せず、必要な修正はエージェントへの指示で反映する。

### 6.3 レビュー・監査観点

- セキュリティ: 禁止事項、インジェクション対策、機密情報保護
- プライバシー: データ分類、保持期間、削除要求対応
- 開発プロセス: ブランチ運用、PR テンプレート、責任者承認
- 実効性: エージェント出力でルールが再現可能か
- 監査: 適用中のガードレールバージョンの明示、例外の理由・責任者・期限、
  変更履歴が `CHANGELOG.md` と `docs/records/` に残ること

## 7. ロールバック方針

- 問題が発生した場合は、直前の安定版ルールセットへ戻す。
- ロールバック時は、戻した理由と影響を `docs/records/managed/DESIGN.md` へ反映する。
- 暫定運用（例外）を行う場合は、期限付きで `docs/records/managed/BACKLOG.md` に登録する。

## 8. 導入完了チェックリスト

「1. 導入資産一覧」の配置確認に加えて、以下を確認します。

- `templates/app-guardrail-template.yaml` の `replace-me` を解消済み
- `templates/model-risk-register-template.csv` に初期リスクを登録済み
- `.github/CODEOWNERS` の `@your-org/...` を実在チームへ置換済み
- `CONTRIBUTING.md` に規定ブランチ、レビュー責任者、例外運用を明記済み
- `docs/records/managed/*.md` に `COPILOT_RECORDS:BEGIN` / `COPILOT_RECORDS:END` が存在
- `CHANGELOG.md` と `docs/records/` の更新ルールをチームで合意済み
- `CLAUDE.md` の `@import` 対象を最小限に絞り済み（「1.1」参照）
- `CLAUDE.md`「本リポジトリの品質ゲート定義」を自プロジェクトのコマンドへ差し替え済み
- 自律ループ実行モードの採否を決定済み（「1.2」参照）
- `.github/copilot-instructions.md` を共通規約の正本として配置済み（Claude Code のみの利用でも必須）
- 共通規約とエージェント固有差分の役割分担が `CONTRIBUTING.md`
  「エージェント指示ファイルの構成規約」に沿っていることを確認済み

## 9. 既知の制約

- プロンプト指示だけでは、アプリ実行時の強制ブロックは保証できない。
- 本番での最終強制は、実装コード、ポリシーエンジン、運用監視で補完する必要がある。
