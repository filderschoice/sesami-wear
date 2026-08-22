<#
.SYNOPSIS
    mobile（wearをdynamic featureとして含む）の署名付きリリースAAB（Android App Bundle）を
    ビルドする（BL-035、BL-036でmobile/wearのapplicationIdを統合）。

.DESCRIPTION
    scripts/version.properties に記録した現在のバージョンを基準に、versionCodeを1インクリメント
    してビルドする。-VersionCode / -VersionName を指定した場合は、その値を固定でそのまま使用する
    （インクリメントしない）。ビルド成功時のみ version.properties を新しい値へ更新する。
    wearはmobileのdynamic featureとして統合されているため、mobileのAAB1本にwear分も含まれる
    （:wear:bundleReleaseという独立タスクはfeatureモジュール単体では実行できない）。

.PARAMETER VersionCode
    固定で使用するversionCode（整数）。省略時は現在値から1インクリメントする。

.PARAMETER VersionName
    固定で使用するversionName（文字列）。省略時は現在のversionNameをそのまま維持する。

.EXAMPLE
    scripts\release-build.bat
    versionCodeを1インクリメントしてビルドする（versionNameは変更しない）。

.EXAMPLE
    scripts\release-build.bat -VersionCode 10 -VersionName 1.1.0
    versionCode=10, versionName=1.1.0 を固定で使用してビルドする。
#>
param(
    [int]$VersionCode,
    [string]$VersionName
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$versionFile = Join-Path $PSScriptRoot "version.properties"

$currentVersionCode = 0
$currentVersionName = "0.1.0"

if (Test-Path $versionFile) {
    foreach ($line in Get-Content $versionFile) {
        if ($line -match "^VERSION_CODE=(.+)$") { $currentVersionCode = [int]$Matches[1] }
        if ($line -match "^VERSION_NAME=(.+)$") { $currentVersionName = $Matches[1] }
    }
} else {
    Write-Warning "scripts/version.propertiesが見つからないため、既定値(VERSION_CODE=0)から開始します。"
}

if ($PSBoundParameters.ContainsKey("VersionCode")) {
    $newVersionCode = $VersionCode
} else {
    $newVersionCode = $currentVersionCode + 1
}

if ($PSBoundParameters.ContainsKey("VersionName")) {
    $newVersionName = $VersionName
} else {
    $newVersionName = $currentVersionName
}

Write-Host "リリースビルドを開始します: versionCode=$newVersionCode, versionName=$newVersionName"

$gradlewPath = Join-Path $repoRoot "gradlew.bat"
if (-not (Test-Path $gradlewPath)) {
    throw "gradlew.batが見つかりません: $gradlewPath"
}

$gradleArgs = @(
    ":mobile:bundleRelease"
    "-PappVersionCode=$newVersionCode"
    "-PappVersionName=$newVersionName"
    "--no-daemon"
)

Push-Location $repoRoot
try {
    & $gradlewPath @gradleArgs
    if ($LASTEXITCODE -ne 0) {
        throw "Gradleビルドが失敗しました（終了コード: $LASTEXITCODE）。version.propertiesは更新していません。"
    }
} finally {
    Pop-Location
}

"VERSION_CODE=$newVersionCode`nVERSION_NAME=$newVersionName" | Set-Content -Path $versionFile -Encoding utf8

Write-Host ""
Write-Host "リリースビルドが完了しました: versionCode=$newVersionCode, versionName=$newVersionName"
Write-Host "  AAB（wearをdynamic featureとして含む）: mobile/build/outputs/bundle/release/mobile-release.aab"
Write-Host ""
Write-Host "署名設定（local.properties）が未構成の場合、上記AABはunsignedのままです。"
Write-Host "署名手順はREADME.md「リリースビルド・Google Play公開」を参照してください。"
