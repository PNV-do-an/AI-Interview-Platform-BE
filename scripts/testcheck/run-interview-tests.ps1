# Test cho module interview: AI sinh câu hỏi, chương trình ôn tập, phiên phỏng vấn mô phỏng/avatar,
# nhắc lịch và kho câu hỏi. SlotServiceImplTest nằm trong run-core-tests.ps1 (luồng đặt lịch).
$root = 'src/test/java/com/aiinterview/platform/service/interview/impl'

$sources = @(
  "$root/AIQuestionServiceImplTest.java",
  "$root/StudyProgramServiceImplTest.java",
  "$root/MockInterviewServiceImplTest.java",
  "$root/InterviewReminderServiceImplTest.java",
  "$root/QuestionPoolServiceImplTest.java",
  'src/test/java/com/aiinterview/platform/service/ai/AvatarReportEvaluatorTest.java'
)

$tests = @(
  'com.aiinterview.platform.service.interview.impl.AIQuestionServiceImplTest',
  'com.aiinterview.platform.service.interview.impl.StudyProgramServiceImplTest',
  'com.aiinterview.platform.service.interview.impl.MockInterviewServiceImplTest',
  'com.aiinterview.platform.service.interview.impl.InterviewReminderServiceImplTest',
  'com.aiinterview.platform.service.interview.impl.QuestionPoolServiceImplTest',
  'com.aiinterview.platform.service.ai.AvatarReportEvaluatorTest'
)

& "$PSScriptRoot\compile-and-run.ps1" -SourceList ($sources -join ',') -TestList ($tests -join ',')
exit $LASTEXITCODE
