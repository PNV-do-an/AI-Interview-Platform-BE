# Test cho luong nghiep vu chinh (core flow): dang tin tuyen dung, ung tuyen, dat lich,
# cover letter, ho so ung vien, danh gia, bao loi, don hang, goi dich vu.
# Day la bo "P0" cu (uu tien so 0) - khong phai Postman folder 00.
$root = 'src/test/java/com/aiinterview/platform/service'

$sources = @(
  "$root/job/impl/JobServiceImplTest.java",
  "$root/job/impl/ApplicationServiceImplTest.java",
  "$root/interview/impl/SlotServiceImplTest.java",
  "$root/document/impl/CoverLetterServiceImplTest.java",
  "$root/user/impl/CandidateProfileServiceImplTest.java",
  "$root/feedback/impl/ReviewServiceImplTest.java",
  "$root/feedback/impl/BugReportServiceImplTest.java",
  "$root/commerce/impl/OrderServiceImplTest.java",
  "$root/commerce/impl/ServicePackageServiceImplTest.java",
  "$root/job/impl/MentorServiceImplTest.java",
  "$root/job/impl/MatchingServiceImplTest.java",
  "$root/job/impl/PositionRecommendationServiceImplTest.java",
  "$root/document/impl/CvServiceImplTest.java",
  'src/test/java/com/aiinterview/platform/model/dto/request/JobRequestValidationTest.java'
)

$tests = @(
  'com.aiinterview.platform.service.job.impl.JobServiceImplTest',
  'com.aiinterview.platform.service.job.impl.ApplicationServiceImplTest',
  'com.aiinterview.platform.service.interview.impl.SlotServiceImplTest',
  'com.aiinterview.platform.service.document.impl.CoverLetterServiceImplTest',
  'com.aiinterview.platform.service.user.impl.CandidateProfileServiceImplTest',
  'com.aiinterview.platform.service.feedback.impl.ReviewServiceImplTest',
  'com.aiinterview.platform.service.feedback.impl.BugReportServiceImplTest',
  'com.aiinterview.platform.service.commerce.impl.OrderServiceImplTest',
  'com.aiinterview.platform.service.commerce.impl.ServicePackageServiceImplTest',
  'com.aiinterview.platform.service.job.impl.MentorServiceImplTest',
  'com.aiinterview.platform.service.job.impl.MatchingServiceImplTest',
  'com.aiinterview.platform.service.job.impl.PositionRecommendationServiceImplTest',
  'com.aiinterview.platform.service.document.impl.CvServiceImplTest',
  'com.aiinterview.platform.model.dto.request.JobRequestValidationTest'
)

& "$PSScriptRoot\compile-and-run.ps1" -SourceList ($sources -join ',') -TestList ($tests -join ',')
exit $LASTEXITCODE
