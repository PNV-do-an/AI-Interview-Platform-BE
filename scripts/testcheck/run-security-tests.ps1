# Batch 1 - Security core: JWT, filter, session store, profile/password writes.
# Khong can Docker: tat ca deu la unit test voi mock.
$root = 'src/test/java/com/aiinterview/platform'

$sources = @(
  "$root/security/jwt/JwtUtilTest.java",
  "$root/security/jwt/JwtFilterTest.java",
  "$root/security/utils/JwtUtilsTest.java",
  "$root/security/utils/SecurityUtilsTest.java",
  "$root/security/utils/UserAgentParserTest.java",
  "$root/service/auth/impl/UserSessionServiceImplTest.java",
  "$root/service/auth/impl/UserServiceImplTest.java"
)

$tests = @(
  'com.aiinterview.platform.security.jwt.JwtUtilTest',
  'com.aiinterview.platform.security.jwt.JwtFilterTest',
  'com.aiinterview.platform.security.utils.JwtUtilsTest',
  'com.aiinterview.platform.security.utils.SecurityUtilsTest',
  'com.aiinterview.platform.security.utils.UserAgentParserTest',
  'com.aiinterview.platform.service.auth.impl.UserSessionServiceImplTest',
  'com.aiinterview.platform.service.auth.impl.UserServiceImplTest'
)

& "$PSScriptRoot\compile-and-run.ps1" -SourceList ($sources -join ',') -TestList ($tests -join ',')
exit $LASTEXITCODE
