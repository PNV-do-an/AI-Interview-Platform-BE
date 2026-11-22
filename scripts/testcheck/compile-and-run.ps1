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
# javac ghi "Note: ... uses unchecked or unsafe operations" ra stderr; voi
# $ErrorActionPreference='Stop' thi PS 5.1 bien dong do thanh NativeCommandError va script
# chet truoc khi chay duoc test nao (exit 1, khong co dong "N successful").
# Vi vay: ha tam thoi ve 'Continue' roi tra lai, va gom stderr vao file de khong spam console.
function Invoke-Javac([string]$argFile, [string]$label) {
  $errLog = Join-Path $env:TEMP 'javac-stderr.txt'
  $saved = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  & $javac "@$argFile" 2>$errLog
  $code = $LASTEXITCODE
  $ErrorActionPreference = $saved
  if ($code -ne 0) {
    if (Test-Path $errLog) { Get-Content $errLog | Write-Host }
    Write-Host "$label FAILED"
    exit 1
  }
}

Invoke-Javac (Join-Path $env:TEMP 'javac-args.txt') 'COMPILE'

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

Invoke-Javac $runnerArgs 'RUNNER COMPILE'

$classes = $TestList -split ','
# JVM cung ghi canh bao ra stderr (vi du "Sharing is only supported for boot loader
# classes..."), nen ap cung cach xu ly nhu javac: 'Continue' + gom stderr vao file.
$javaErr = Join-Path $env:TEMP 'testrun-stderr.txt'
$savedEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
& $java -cp $cp TestRunner @classes 2>$javaErr
$runCode = $LASTEXITCODE
$ErrorActionPreference = $savedEap
if ($runCode -ne 0 -and (Test-Path $javaErr)) { Get-Content $javaErr | Write-Host }
exit $runCode
