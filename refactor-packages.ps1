$ErrorActionPreference = "Stop"
$root = "c:\Users\LENOVO\Downloads\AI-Interview-Platform-BE"
$srcRoot = Join-Path $root "src"
$base = Join-Path $srcRoot "main\java\com\aiinterview\platform"
$utf8 = New-Object System.Text.UTF8Encoding $false

$moves = @(
  @{ cls = "EmailService";     from = "service";      toPkg = "com.aiinterview.platform.service.infra";      toDir = "service\infra" },
  @{ cls = "EmailServiceImpl"; from = "service\impl"; toPkg = "com.aiinterview.platform.service.infra.impl"; toDir = "service\infra\impl" },
  @{ cls = "OpenAiClient";     from = "service";      toPkg = "com.aiinterview.platform.service.infra";      toDir = "service\infra" }
)

$renames = @()
foreach ($m in $moves) {
  $oldPath = Join-Path $base "$($m.from)\$($m.cls).java"
  $newDir  = Join-Path $base $m.toDir
  if (-not (Test-Path $newDir)) { New-Item -ItemType Directory -Path $newDir -Force | Out-Null }
  $newPath = Join-Path $newDir "$($m.cls).java"

  $text = [System.IO.File]::ReadAllText($oldPath)
  $oldPkg = [regex]::Match($text, '(?m)^\s*package\s+([\w\.]+);').Groups[1].Value
  $renames += @{ old = "$oldPkg.$($m.cls)"; new = "$($m.toPkg).$($m.cls)" }

  & git -C $root mv $oldPath $newPath
  if ($LASTEXITCODE -ne 0) { throw "git mv failed: $($m.cls)" }

  $newText = [regex]::Replace($text, '(?m)^\s*package\s+[\w\.]+;', "package $($m.toPkg);")
  [System.IO.File]::WriteAllText($newPath, $newText, $utf8)
}

foreach ($f in (Get-ChildItem -Path $srcRoot -Recurse -File -Filter "*.java")) {
  $t = [System.IO.File]::ReadAllText($f.FullName)
  $orig = $t
  foreach ($r in $renames) { $t = $t.Replace($r.old, $r.new) }
  if ($t -ne $orig) {
    [System.IO.File]::WriteAllText($f.FullName, $t, $utf8)
    Write-Host "rewrote refs: $($f.Name)"
  }
}

# don goi thu muc rong con lai
$stale = Join-Path $base "service\impl"
if ((Test-Path $stale) -and -not (Get-ChildItem $stale -Recurse -File)) { Remove-Item $stale -Force }
Write-Host "phase 2 done"
