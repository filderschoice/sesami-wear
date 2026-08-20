@echo off
rem リリースビルド(署名付きAAB)を実行するバッチファイル(BL-035)。
rem 実処理はrelease-build.ps1(PowerShell)に委譲する。使い方はREADME.md参照。
rem pwsh(PowerShell 7)が使える場合はそちらを優先する
rem (Windows PowerShell 5.1はスクリプト内の日本語コメントの扱いで問題が起きることがあるため)。
rem 例: release-build.bat
rem 例: release-build.bat -VersionCode 10 -VersionName 1.1.0
where pwsh >nul 2>nul
if %errorlevel%==0 (
    pwsh -NoProfile -ExecutionPolicy Bypass -File "%~dp0release-build.ps1" %*
) else (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0release-build.ps1" %*
)
exit /b %errorlevel%
