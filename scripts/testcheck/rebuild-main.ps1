param([string]$SourceList = '')

# Biên dịch lại MỘT VÀI file src/main đã sửa, ghi thẳng vào target/classes.
#
# Vì sao cần: repo này không có Maven (không có .mvn/wrapper/maven-wrapper.properties)
# và harness test chỉ biên dịch src/test. Nếu sửa constructor của một service mà không
# rebuild, test sẽ biên dịch được nhưng lúc chạy lại ném NoSuchMethodError.
#
# Vì sao phải có Lombok: target/classes do IDE build kèm annotation processing. Chạy
# javac -proc:none sẽ làm @RequiredArgsConstructor / @Getter biến mất -> class vẫn
# compile nhưng constructor thiếu tham số. Vì vậy script bắt buộc -processorpath lombok
# và tự kiểm tra lại constructor sau khi build.
#
# Dùng:
#   .\rebuild-main.ps1                       # danh sách mặc định (Batch 2: OAuth R1)
#   .\rebuild-main.ps1 -SourceList 'src/main/java/.../A.java,src/main/java/.../B.java'
$ErrorActionPreference = 'Stop'

$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$classesDir = Join-Path $repo 'target\classes'
$javac = Join-Path $env:JAVA_HOME 'bin\javac.exe'
$javap = Join-Path $env:JAVA_HOME 'bin\javap.exe'
$lombok = Join-Path $env:USERPROFILE '.m2\repository\org\projectlombok\lombok\1.18.36\lombok-1.18.36.jar'

if (-not (Test-Path $classesDir)) {
  Write-Host "THIEU $classesDir - phai build ca src/main truoc (IDE hoac mvn compile)"
  exit 1
}
if (-not (Test-Path $lombok)) {
  Write-Host "THIEU lombok jar: $lombok"
  exit 1
}

# Danh sach mac dinh = nhung file src/main da sua trong Batch 2 (refactor R1).
if ([string]::IsNullOrWhiteSpace($SourceList)) {
  $SourceList = @(
    'src/main/java/com/aiinterview/platform/config/HttpClientConfig.java',
    'src/main/java/com/aiinterview/platform/service/auth/impl/GoogleAuthInterfaceImpl.java',
    'src/main/java/com/aiinterview/platform/service/auth/impl/GithubAuthInterfaceImpl.java',
    'src/main/java/com/aiinterview/platform/service/auth/impl/SsoAuthInterfaceImpl.java'
  ) -join ','
}

$files = @($SourceList -split ',' | ForEach-Object { $_.Trim() } | Where-Object { $_ })
foreach ($f in $files) {
  if (-not (Test-Path (Join-Path $repo $f))) { Write-Host "KHONG TIM THAY source: $f"; exit 1 }
}

# build-classpath.ps1 cần -SourceList; ta chi dùng test-cp.txt nó sinh ra.
& "$PSScriptRoot\build-classpath.ps1" -SourceList '' | Out-Null
$cp = (Get-Content (Join-Path $env:TEMP 'test-cp.txt') -Raw).Trim()
# Lombok phai co ca tren classpath (de resolve `import lombok.*`) lan processorpath.
$cp = "$cp;$($lombok.Replace('\', '/'))"

$argfile = Join-Path $env:TEMP 'javac-main.txt'
$lines = @(
  '-encoding UTF-8'
  '-nowarn'
  "-processorpath '$($lombok.Replace('\', '/'))'"
  "-d '$($classesDir.Replace('\', '/'))'"
  "-cp '$cp'"
)
foreach ($f in $files) {
  $lines += "'" + (Join-Path $repo $f).Replace('\', '/') + "'"
}
# javac từ chối @argfile có BOM
[System.IO.File]::WriteAllLines($argfile, $lines, (New-Object System.Text.UTF8Encoding($false)))

Write-Host "Rebuild $($files.Count) file src/main -> target/classes"
foreach ($f in $files) { Write-Host "  $f" }

& $javac "@$argfile"
if ($LASTEXITCODE -ne 0) { Write-Host 'REBUILD MAIN FAILED'; exit 1 }

# Chot chan: neu Lombok khong chay thi constructor se thieu tham so va test chet luc runtime.
$guards = @{
  'com.aiinterview.platform.service.auth.impl.GoogleAuthInterfaceImpl' = 'java.net.http.HttpClient'
  'com.aiinterview.platform.service.auth.impl.GithubAuthInterfaceImpl' = 'java.net.http.HttpClient'
  'com.aiinterview.platform.service.auth.impl.SsoAuthInterfaceImpl' = 'java.net.http.HttpClient'
}
foreach ($fqn in $guards.Keys) {
  $ctor = & $javap -p -cp $classesDir $fqn | Select-String "$($fqn.Split('.')[-1])\("
  if (-not ($ctor -join ' ' | Select-String ([regex]::Escape($guards[$fqn])))) {
    Write-Host "CANH BAO: $fqn khong co tham so $($guards[$fqn]) - Lombok da khong chay?"
    exit 1
  }
}
Write-Host 'OK: constructor da co HttpClient (Lombok da chay)'
exit 0
