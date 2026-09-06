<#
.SYNOPSIS
    スマホ用（mobile）とウォッチ用（wear）の署名付きリリースAAB（Android App Bundle）を
    まとめてビルドする（BL-035、BL-093）。

.DESCRIPTION
    scripts/version.properties に記録した現在のバージョンを基準に、両方のversionCodeを
    1インクリメントしてビルドする。個別のパラメータを指定した場合は、その値を固定でそのまま
    使用する（インクリメントしない）。ビルド成功時のみ version.properties を新しい値へ更新する。

    wearはBL-090で独立したapplicationモジュールへ分離しており、mobileとは別のAABとして
    ビルドされる。Googleは単一App BundleへWear OSをdynamic featureとして同梱する構成を
    非サポートとしており、Play ConsoleはWear OS向けリリースを専用トラックで公開することを
    必須としているため、2つの成果物を別々のトラックへアップロードする。

    applicationIdは両成果物とも com.sesamiwear.mobile で共通だが、versionCodeは全フォーム
    ファクタで一意である必要があるため、mobileを1始まり、wearを1001始まりの独立した系列で
    管理する。versionNameは利用者から見たアプリのバージョンであるため両者で共通とする。

.PARAMETER VersionCode
    固定で使用するmobileのversionCode（整数）。省略時は現在値から1インクリメントする。

.PARAMETER WearVersionCode
    固定で使用するwearのversionCode（整数）。省略時は現在値から1インクリメントする。

.PARAMETER VersionName
    固定で使用するversionName（文字列、mobile/wear共通）。省略時は現在値をそのまま維持する。

.EXAMPLE
    scripts\release-build.bat
    両方のversionCodeを1インクリメントしてビルドする（versionNameは変更しない）。

.EXAMPLE
    scripts\release-build.bat -VersionCode 10 -WearVersionCode 1010 -VersionName 1.1.0
    指定した値を固定で使用してビルドする。
#>
param(
    [int]$VersionCode,
    [int]$WearVersionCode,
    [string]$VersionName
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$versionFile = Join-Path $PSScriptRoot "version.properties"

$currentVersionCode = 0
$currentWearVersionCode = 1000
$currentVersionName = "0.9.0"

if (Test-Path $versionFile) {
    foreach ($line in Get-Content $versionFile) {
        if ($line -match "^VERSION_CODE=(.+)$") { $currentVersionCode = [int]$Matches[1] }
        if ($line -match "^WEAR_VERSION_CODE=(.+)$") { $currentWearVersionCode = [int]$Matches[1] }
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

if ($PSBoundParameters.ContainsKey("WearVersionCode")) {
    $newWearVersionCode = $WearVersionCode
} else {
    $newWearVersionCode = $currentWearVersionCode + 1
}

if ($PSBoundParameters.ContainsKey("VersionName")) {
    $newVersionName = $VersionName
} else {
    $newVersionName = $currentVersionName
}

if ($newVersionCode -eq $newWearVersionCode) {
    throw "mobileとwearのversionCodeが同一です（$newVersionCode）。Google PlayはversionCodeが全フォームファクタで一意であることを要求します。"
}

Write-Host "リリースビルドを開始します: versionName=$newVersionName"
Write-Host "  mobile versionCode=$newVersionCode / wear versionCode=$newWearVersionCode"

$gradlewPath = Join-Path $repoRoot "gradlew.bat"
if (-not (Test-Path $gradlewPath)) {
    throw "gradlew.batが見つかりません: $gradlewPath"
}

$gradleArgs = @(
    ":mobile:bundleRelease"
    ":wear:bundleRelease"
    "-PappVersionCode=$newVersionCode"
    "-PappVersionName=$newVersionName"
    "-PappWearVersionCode=$newWearVersionCode"
    "-PappWearVersionName=$newVersionName"
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

# Set-Contentは環境依存の改行コードを付与し、実行のたびに無意味なgit差分が出るため、
# 改行をLFに固定しBOMなしUTF-8で書き出す（BL-093）。
$versionContent = "VERSION_CODE=$newVersionCode`nWEAR_VERSION_CODE=$newWearVersionCode`nVERSION_NAME=$newVersionName`n"
[System.IO.File]::WriteAllText($versionFile, $versionContent, (New-Object System.Text.UTF8Encoding $false))

Write-Host ""
Write-Host "リリースビルドが完了しました: versionName=$newVersionName"
Write-Host "  スマホ用AAB（versionCode=$newVersionCode）: mobile/build/outputs/bundle/release/mobile-release.aab"
Write-Host "    → Play Consoleの「電話・タブレット」系トラックへアップロードする"
Write-Host "  ウォッチ用AAB（versionCode=$newWearVersionCode）: wear/build/outputs/bundle/release/wear-release.aab"
Write-Host "    → Play Consoleの「Wear OS」専用トラックへアップロードする"
Write-Host ""
Write-Host "署名設定（local.properties）が未構成の場合、上記AABはunsignedのままです。"
Write-Host "署名手順はREADME.md「リリースビルド・Google Play公開」を参照してください。"
