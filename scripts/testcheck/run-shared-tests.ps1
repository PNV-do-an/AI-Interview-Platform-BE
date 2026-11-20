# Test cho phần dùng chung (không thuộc một module nghiệp vụ nào): xử lý lỗi toàn cục,
# chuẩn hóa/tách/đối chiếu kỹ năng, và validate request DTO.
# Đây là các class không cần Spring context, không cần MySQL -> chạy offline.
$root = 'src/test/java/com/aiinterview/platform'

$sources = @(
  "$root/common/exception/GlobalExceptionHandlerTest.java",
  "$root/common/util/SkillKeysTest.java",
  "$root/common/util/SkillMatcherTest.java",
  "$root/model/dto/request/RegisterRequestValidationTest.java"
)

$tests = @(
  'com.aiinterview.platform.common.exception.GlobalExceptionHandlerTest',
  'com.aiinterview.platform.common.util.SkillKeysTest',
  'com.aiinterview.platform.common.util.SkillMatcherTest',
  'com.aiinterview.platform.model.dto.request.RegisterRequestValidationTest'
)

& "$PSScriptRoot\compile-and-run.ps1" -SourceList ($sources -join ',') -TestList ($tests -join ',')
exit $LASTEXITCODE
