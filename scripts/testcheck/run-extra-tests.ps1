# Batch 3 - cac test tung nam ngoai harness (40 test / 8 class), chay duoc offline.
# Khong gom TwoFactorIntegrationTest vi no can Docker + MySQL (@SpringBootTest + Testcontainers).
$root = 'src/test/java/com/aiinterview/platform'

$sources = @(
  "$root/service/job/impl/PositionRecommendationServiceImplTest.java",
  "$root/service/interview/impl/QuestionPoolServiceImplTest.java",
  "$root/service/interview/impl/InterviewReminderServiceImplTest.java",
  "$root/service/document/impl/CvServiceImplTest.java",
  "$root/service/ai/AvatarReportEvaluatorTest.java",
  "$root/service/admin/impl/AdminPositionServiceImplTest.java",
  "$root/service/infra/impl/EmailServiceImplGmailTest.java",
  "$root/model/dto/request/RegisterRequestValidationTest.java"
)

$tests = @(
  'com.aiinterview.platform.service.job.impl.PositionRecommendationServiceImplTest',
  'com.aiinterview.platform.service.interview.impl.QuestionPoolServiceImplTest',
  'com.aiinterview.platform.service.interview.impl.InterviewReminderServiceImplTest',
  'com.aiinterview.platform.service.document.impl.CvServiceImplTest',
  'com.aiinterview.platform.service.ai.AvatarReportEvaluatorTest',
  'com.aiinterview.platform.service.admin.impl.AdminPositionServiceImplTest',
  'com.aiinterview.platform.service.infra.impl.EmailServiceImplGmailTest',
  'com.aiinterview.platform.model.dto.request.RegisterRequestValidationTest'
)

& "$PSScriptRoot\compile-and-run.ps1" -SourceList ($sources -join ',') -TestList ($tests -join ',')
exit $LASTEXITCODE
