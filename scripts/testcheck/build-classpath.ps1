param([string]$SourceList)

# Helper: dựng @argfile cho javac và classpath runtime (không chạy test).
# Được gọi từ compile-and-run.ps1, không cần gọi tay.
$Sources = $SourceList -split ','

$m2 = "$env:USERPROFILE\.m2\repository"

# Jar nào cần cho unit test offline. Version phải khớp pom.xml (Spring Boot 3.2.5).
# Cần dependency mới -> thêm đúng version ở đây, nếu không sẽ lỗi rất khó đọc về sau.
$allow = @(
  'org/springframework/spring-core/6.1.6/spring-core-6.1.6.jar',
  'org/springframework/spring-beans/6.1.6/spring-beans-6.1.6.jar',
  'org/springframework/spring-context/6.1.6/spring-context-6.1.6.jar',
  'org/springframework/spring-aop/6.1.6/spring-aop-6.1.6.jar',
  'org/springframework/spring-expression/6.1.6/spring-expression-6.1.6.jar',
  'org/springframework/spring-jcl/6.1.6/spring-jcl-6.1.6.jar',
  'org/springframework/spring-tx/6.1.6/spring-tx-6.1.6.jar',
  'org/springframework/spring-web/6.1.6/spring-web-6.1.6.jar',
  'org/springframework/spring-test/6.1.6/spring-test-6.1.6.jar',
  'org/springframework/data/spring-data-commons/3.2.5/spring-data-commons-3.2.5.jar',
  'org/springframework/data/spring-data-jpa/3.2.5/spring-data-jpa-3.2.5.jar',
  'jakarta/transaction/jakarta.transaction-api/2.0.1/jakarta.transaction-api-2.0.1.jar',
  'org/springframework/security/spring-security-core/6.2.4/spring-security-core-6.2.4.jar',
  'org/springframework/security/spring-security-crypto/6.2.4/spring-security-crypto-6.2.4.jar',
  'org/junit/jupiter/junit-jupiter-api/5.10.2/junit-jupiter-api-5.10.2.jar',
  'org/junit/jupiter/junit-jupiter-params/5.10.2/junit-jupiter-params-5.10.2.jar',
  'org/junit/jupiter/junit-jupiter-engine/5.10.2/junit-jupiter-engine-5.10.2.jar',
  'org/junit/platform/junit-platform-commons/1.10.2/junit-platform-commons-1.10.2.jar',
  'org/junit/platform/junit-platform-engine/1.10.2/junit-platform-engine-1.10.2.jar',
  'org/junit/platform/junit-platform-launcher/1.10.2/junit-platform-launcher-1.10.2.jar',
  'io/jsonwebtoken/jjwt-api/0.11.5/jjwt-api-0.11.5.jar',
  'io/jsonwebtoken/jjwt-impl/0.11.5/jjwt-impl-0.11.5.jar',
  'io/jsonwebtoken/jjwt-jackson/0.11.5/jjwt-jackson-0.11.5.jar',
  'com/fasterxml/jackson/core/jackson-databind/2.15.4/jackson-databind-2.15.4.jar',
  'com/fasterxml/jackson/core/jackson-core/2.15.4/jackson-core-2.15.4.jar',
  'com/fasterxml/jackson/core/jackson-annotations/2.15.4/jackson-annotations-2.15.4.jar',
  'dev/samstevens/totp/totp/1.7.1/totp-1.7.1.jar',
  'net/bytebuddy/byte-buddy/1.14.13/byte-buddy-1.14.13.jar',
  'net/bytebuddy/byte-buddy-agent/1.14.13/byte-buddy-agent-1.14.13.jar',
  'org/mockito/mockito-core/5.7.0/mockito-core-5.7.0.jar',
  'org/mockito/mockito-junit-jupiter/5.7.0/mockito-junit-jupiter-5.7.0.jar',
  'org/assertj/assertj-core/3.24.2/assertj-core-3.24.2.jar',
  'org/slf4j/slf4j-api/2.0.13/slf4j-api-2.0.13.jar',
  'jakarta/persistence/jakarta.persistence-api/3.1.0/jakarta.persistence-api-3.1.0.jar',
  'jakarta/validation/jakarta.validation-api/3.0.2/jakarta.validation-api-3.0.2.jar',
  'org/hibernate/validator/hibernate-validator/8.0.1.Final/hibernate-validator-8.0.1.Final.jar',
  'com/fasterxml/classmate/1.6.0/classmate-1.6.0.jar',
  'org/jboss/logging/jboss-logging/3.5.3.Final/jboss-logging-3.5.3.Final.jar',
  'org/apache/tomcat/embed/tomcat-embed-el/10.1.20/tomcat-embed-el-10.1.20.jar',
  'org/apache/tomcat/embed/tomcat-embed-core/10.1.20/tomcat-embed-core-10.1.20.jar',
  'org/objenesis/objenesis/3.3/objenesis-3.3.jar',
  'org/opentest4j/opentest4j/1.2.0/opentest4j-1.2.0.jar',
  'org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar'
)

$cp = @("$($env:TEMP.Replace('\','/'))/testcheck", 'target/classes')
foreach ($rel in $allow) {
  $full = Join-Path $m2 ($rel -replace '/', '\')
  if (Test-Path $full) { $cp += $full.Replace('\', '/') }
  else { Write-Host "MISSING JAR: $rel" }
}

# Classpath runtime cho JUnit launcher, đọc lại ở compile-and-run.ps1
[System.IO.File]::WriteAllText((Join-Path $env:TEMP 'test-cp.txt'), ($cp -join ';'),
  (New-Object System.Text.UTF8Encoding($false)))

$lines = @(
  '-proc:none',
  '-encoding UTF-8',
  '-nowarn',
  "-d '$($env:TEMP.Replace('\','/'))/testcheck'",
  "-cp '$($cp -join ';')'"
)
foreach ($src in $Sources) {
  $lines += "'" + $src.Trim().Replace('\', '/') + "'"
}

$out = Join-Path $env:TEMP 'javac-args.txt'
Set-Content -Path $out -Value $lines -Encoding UTF8
# javac từ chối @argfile có BOM, nên ghi lại không BOM
[System.IO.File]::WriteAllLines($out, $lines, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Wrote $out with $($Sources.Count) sources and $($cp.Count) classpath entries"
