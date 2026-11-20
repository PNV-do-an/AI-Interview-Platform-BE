# Test cho Postman folder 00 (Auth): dang ky, dang nhap, refresh, me, logout, OAuth, quen mat khau, 2FA.
# Khong gom TwoFactorIntegrationTest vi no can Docker + MySQL (@SpringBootTest + Testcontainers).
$root = 'src/test/java/com/aiinterview/platform'

$sources = @(
  "$root/service/auth/impl/LoginInterfaceImplTest.java",
  "$root/service/auth/impl/GoogleAuthInterfaceImplTest.java",
  "$root/service/auth/impl/GithubAuthInterfaceImplTest.java",
  "$root/controller/auth/AuthControllerSessionTest.java",
  "$root/service/auth/impl/RegisterInterfaceImplTest.java",
  "$root/service/auth/impl/PasswordResetServiceImplTest.java",
  "$root/service/auth/impl/TwoFactorServiceImplTest.java",
  "$root/service/auth/impl/SsoEmailDomainPolicyTest.java",
  "$root/service/auth/impl/UserSessionServiceImplTest.java",
  "$root/service/auth/impl/UserServiceImplTest.java",
  "$root/service/auth/impl/SsoAuthInterfaceImplTest.java"
)

$tests = @(
  'com.aiinterview.platform.service.auth.impl.LoginInterfaceImplTest',
  'com.aiinterview.platform.service.auth.impl.GoogleAuthInterfaceImplTest',
  'com.aiinterview.platform.service.auth.impl.GithubAuthInterfaceImplTest',
  'com.aiinterview.platform.controller.auth.AuthControllerSessionTest',
  'com.aiinterview.platform.service.auth.impl.RegisterInterfaceImplTest',
  'com.aiinterview.platform.service.auth.impl.PasswordResetServiceImplTest',
  'com.aiinterview.platform.service.auth.impl.TwoFactorServiceImplTest',
  'com.aiinterview.platform.service.auth.impl.SsoEmailDomainPolicyTest',
  'com.aiinterview.platform.service.auth.impl.UserSessionServiceImplTest',
  'com.aiinterview.platform.service.auth.impl.UserServiceImplTest',
  'com.aiinterview.platform.service.auth.impl.SsoAuthInterfaceImplTest'
)

& "$PSScriptRoot\compile-and-run.ps1" -SourceList ($sources -join ',') -TestList ($tests -join ',')
exit $LASTEXITCODE
