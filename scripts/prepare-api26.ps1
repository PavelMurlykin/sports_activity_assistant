param(
    [Parameter(Mandatory = $true)][string]$SdkPath,
    [string]$TestRoot = (Join-Path $PSScriptRoot '..\.local-testing\api26')
)
$ErrorActionPreference = 'Stop'
$TestRoot = [IO.Path]::GetFullPath($TestRoot)
# Reuse the SDK agreement already accepted by the developer. Never accept new terms here.
$licenseFile = Join-Path $SdkPath 'licenses\android-sdk-license'
if (!(Test-Path -LiteralPath $licenseFile) -or
    !(Select-String -LiteralPath $licenseFile -SimpleMatch '24333f8a63b6825ea9c5514f83c2829b004d1fee' -Quiet)) {
    throw 'Configure the Android SDK and accept its license yourself before running this script.'
}
New-Item -ItemType Directory -Path $TestRoot -Force | Out-Null
[xml]$metadata = (Invoke-WebRequest 'https://dl.google.com/android/repository/sys-img/google_apis/sys-img2-1.xml').Content
$package = $metadata.SelectSingleNode("//*[local-name()='remotePackage' and @path='system-images;android-26;google_apis;x86_64']")
if (!$package) { throw 'Official API 26 x86_64 package not found.' }
$archive = $package.SelectSingleNode(".//*[local-name()='complete']")
$fileName = $archive.SelectSingleNode("*[local-name()='url']").InnerText
$expectedSha1 = $archive.SelectSingleNode("*[local-name()='checksum']").InnerText
$expectedSize = [long]$archive.SelectSingleNode("*[local-name()='size']").InnerText
$zip = Join-Path $TestRoot $fileName
if (!(Test-Path -LiteralPath $zip) -or (Get-Item -LiteralPath $zip).Length -lt $expectedSize) {
    & curl.exe --fail --location --continue-at - --retry 3 --output $zip "https://dl.google.com/android/repository/sys-img/google_apis/$fileName"
    if ($LASTEXITCODE -ne 0) { throw 'Download interrupted; rerun to resume the same official archive.' }
}
if ((Get-FileHash -LiteralPath $zip -Algorithm SHA1).Hash -ne $expectedSha1) {
    throw "Checksum mismatch. Retain the file for diagnosis; do not use it: $zip"
}
$image = Join-Path $TestRoot 'image'
if (!(Test-Path -LiteralPath (Join-Path $image 'x86_64\system.img'))) {
    Expand-Archive -LiteralPath $zip -DestinationPath $image
}
Write-Output "Verified API 26 image: $(Join-Path $image 'x86_64')"
Write-Output "SHA1: $expectedSha1"
Write-Output 'Use the isolated AVD configuration described in docs/stage-11-local-testing.md.'
