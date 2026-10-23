param(
    [string]$OutputPath = (Join-Path $PSScriptRoot '..\docs\privacy-policy.html')
)
$ErrorActionPreference = 'Stop'
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
[xml]$policy = Get-Content -LiteralPath (Join-Path $project 'app\src\main\res\values\app_info.xml') -Raw -Encoding UTF8
[xml]$strings = Get-Content -LiteralPath (Join-Path $project 'app\src\main\res\values\strings.xml') -Raw -Encoding UTF8
function Get-PolicyText([string]$Name) {
    $node = $policy.resources.string | Where-Object { $_.name -eq $Name }
    if (!$node) { throw "Missing policy resource: $Name" }
    [Net.WebUtility]::HtmlEncode($node.InnerText.Replace('\n', "`n"))
}
$title = Get-PolicyText 'privacy_policy_title'
$appName = [Net.WebUtility]::HtmlEncode(($strings.resources.string | Where-Object { $_.name -eq 'app_name' }).InnerText)
$developer = Get-PolicyText 'developer_name'
$email = Get-PolicyText 'developer_email'
$sections = foreach ($node in $policy.resources.string | Where-Object { $_.name -like 'privacy_section_*_title' }) {
    $sectionTitle = Get-PolicyText $node.name
    $body = Get-PolicyText ($node.name -replace '_title$', '_body')
    $contact = if ($node.name -eq 'privacy_section_contact_title') {
        "<address>$developer<br><a href=`"mailto:$email`">$email</a></address>"
    } else { '' }
    "<section><h2>$sectionTitle</h2><p>$body</p>$contact</section>"
}
$html = @"
<!doctype html>
<html lang="ru">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>$title — $appName</title>
  <style>
    :root { color-scheme: light dark; font-family: system-ui, sans-serif; }
    body { max-width: 760px; margin: 0 auto; padding: 24px; line-height: 1.65; overflow-wrap: anywhere; }
    h1 { font-size: 1.8rem; line-height: 1.25; }
    h2 { font-size: 1.2rem; margin-top: 2rem; }
    p { white-space: pre-line; }
    address { font-style: normal; }
    .updated { font-size: .9rem; }
  </style>
</head>
<body>
<main>
  <h1>$title</h1>
  <p><strong>$appName</strong></p>
  <p class="updated">$(Get-PolicyText 'privacy_policy_updated')</p>
  <p>$(Get-PolicyText 'privacy_policy_intro')</p>
  $($sections -join "`n  ")
</main>
</body>
</html>
"@
[IO.File]::WriteAllText([IO.Path]::GetFullPath($OutputPath), $html, [Text.UTF8Encoding]::new($false))
Write-Output "Privacy policy exported to $([IO.Path]::GetFullPath($OutputPath))"
