# Batch 3 - các test từng nằm ngoài harness (49 test / 9 class), chạy được offline.
# Khong gom TwoFactorIntegrationTest vi no can Docker + MySQL (@SpringBootTest + Testcontainers).
# Thưc hiện test các chức năng phụ liên quan đến các chức năng chính. 
$root = 'src/test/java/com/aiinterview/platform'

$sources = @(
  "$root/service/job/impl/PositionRecommendationServiceImplTest.java",
  "$root/common/util/SkillMatcherTest.java",
  "$root/common/util/SkillKeysTest.java",
  "$root/common/util/QuestionContentKeyTest.java",
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
  'com.aiinterview.platform.common.util.SkillMatcherTest',
  'com.aiinterview.platform.common.util.SkillKeysTest',
  'com.aiinterview.platform.common.util.QuestionContentKeyTest',
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
