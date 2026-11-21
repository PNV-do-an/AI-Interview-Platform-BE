param([string]$SourceList, [string]$TestList)

# Engine: biên dịch các file test được liệt kê rồi chạy chúng bằng JUnit Platform.
# Không cần gọi tay — dùng run-auth-tests.ps1 hoặc run-core-tests.ps1.
$ErrorActionPreference = 'Stop'
$tmp = $env:TEMP.Replace('\', '/')
$javac = Join-Path $env:JAVA_HOME 'bin\javac.exe'
$java = Join-Path $env:JAVA_HOME 'bin\java.exe'

if (-not (Test-Path 'target/classes')) {
  Write-Host 'THIEU target/classes - phai build phan src/main truoc (IDE hoac mvn compile)'
  exit 1
}

& "$PSScriptRoot\build-classpath.ps1" -SourceList $SourceList
& $javac "@$env:TEMP\javac-args.txt"
if ($LASTEXITCODE -ne 0) { Write-Host 'COMPILE FAILED'; exit 1 }

$cp = (Get-Content (Join-Path $env:TEMP 'test-cp.txt') -Raw).Trim()

$runnerArgs = Join-Path $env:TEMP 'javac-runner.txt'
[System.IO.File]::WriteAllLines($runnerArgs, @(
  '-proc:none',
  '-encoding UTF-8',
  '-nowarn',
  "-d '$tmp/testcheck'",
  "-cp '$cp'",
  "'$($PSScriptRoot.Replace('\', '/'))/TestRunner.java'"
), (New-Object System.Text.UTF8Encoding($false)))

& $javac "@$runnerArgs"
if ($LASTEXITCODE -ne 0) { Write-Host 'RUNNER COMPILE FAILED'; exit 1 }

$classes = $TestList -split ','
& $java -cp $cp TestRunner @classes
exit $LASTEXITCODE
