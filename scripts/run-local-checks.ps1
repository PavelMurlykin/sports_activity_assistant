param(
    [Parameter(Mandatory = $true)][string]$Serial,
    [Parameter(Mandatory = $true)][string]$SdkPath,
    [Parameter(Mandatory = $true)][int]$ExpectedAndroidTests
)
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-[0-9]+$') { throw 'Use a separate empty test emulator, never a personal device.' }
if ($ExpectedAndroidTests -le 0) { throw 'Provide the expected positive test count.' }
$adb = Join-Path $SdkPath 'platform-tools\adb.exe'
$state = & $adb -s $Serial get-state
if ($LASTEXITCODE -ne 0 -or $state -ne 'device') { throw 'Test emulator is unavailable.' }
$installed = & $adb -s $Serial shell pm list packages com.pamurlykin.sportsactivityassistant
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect installed packages.' }
if ($installed) {
    throw 'App already installed. Use a new empty test AVD. This script will not delete existing history.'
}
$previousSerial = $env:ANDROID_SERIAL
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$started = [DateTime]::UtcNow.AddSeconds(-1)
try {
    $env:ANDROID_SERIAL = $Serial
    & (Join-Path $project 'gradlew.bat') --project-dir $project --offline verifyOfflinePolicy testDebugUnitTest lintDebug assembleDebug assembleRelease connectedDebugAndroidTest --console plain
    if ($LASTEXITCODE -ne 0) { throw 'Gradle verification failed.' }
    $reportDirectory = Join-Path $project 'app\build\outputs\androidTest-results\connected\debug'
    $reports = @(Get-ChildItem -LiteralPath $reportDirectory -Filter '*.xml' | Where-Object { $_.LastWriteTimeUtc -ge $started })
    if (!$reports) { throw 'No fresh Android test report; a successful Gradle exit is not acceptance.' }
    $suites = @($reports | ForEach-Object { [xml]$report = Get-Content -LiteralPath $_.FullName; $report.SelectNodes('//testsuite') })
    $counts = @{}
    foreach ($field in @('tests','failures','errors','skipped')) {
        $counts[$field] = ($suites | Measure-Object -Property $field -Sum).Sum
    }
    if ($counts.tests -ne $ExpectedAndroidTests -or $counts.failures -ne 0 -or $counts.errors -ne 0 -or $counts.skipped -ne 0) {
        throw "Incomplete Android verification: $($counts | ConvertTo-Json -Compress)"
    }
    Write-Output "Verified $ExpectedAndroidTests Android tests without failures, errors or skips."
} finally {
    if ($null -eq $previousSerial) { Remove-Item Env:ANDROID_SERIAL -ErrorAction SilentlyContinue }
    else { $env:ANDROID_SERIAL = $previousSerial }
}
