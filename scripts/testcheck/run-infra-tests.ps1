# Test cho module infra: EmailService (SMTP, Gmail API, dev-log, phân biệt email bắt buộc vs thông báo).
# Chạy hoàn toàn offline: Gmail API được thay bằng HttpServer nội bộ trong EmailServiceImplGmailTest,
# SMTP được thay bằng mock JavaMailSender, nên không cần mạng và không cần credential thật.
$root = 'src/test/java/com/aiinterview/platform/service/infra/impl'

$sources = @(
  "$root/EmailServiceImplGmailTest.java",
  "$root/EmailServiceImplNotificationTest.java"
)

$tests = @(
  'com.aiinterview.platform.service.infra.impl.EmailServiceImplGmailTest',
  'com.aiinterview.platform.service.infra.impl.EmailServiceImplNotificationTest'
)

& "$PSScriptRoot\compile-and-run.ps1" -SourceList ($sources -join ',') -TestList ($tests -join ',')
exit $LASTEXITCODE
