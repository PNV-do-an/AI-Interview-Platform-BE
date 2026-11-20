# Test cho module admin (service/admin + admin dashboard/settings/permission/category).
# Cac IntegrationTest cua admin can MySQL nen KHONG nam o day.
$root = 'src/test/java/com/aiinterview/platform/service/admin/impl'

$sources = @(
  "$root/AdminCategoryServiceImplTest.java",
  "$root/AdminPermissionServiceImplTest.java",
  "$root/AdminDashboardServiceImplTest.java",
  "$root/AdminSettingsServiceImplTest.java",
  "$root/AdminPositionServiceImplTest.java",
  "$root/AdminUserServiceImplTest.java",
  "$root/TopCvImportServiceImplTest.java"
)

$tests = @(
  'com.aiinterview.platform.service.admin.impl.AdminCategoryServiceImplTest',
  'com.aiinterview.platform.service.admin.impl.AdminPermissionServiceImplTest',
  'com.aiinterview.platform.service.admin.impl.AdminDashboardServiceImplTest',
  'com.aiinterview.platform.service.admin.impl.AdminSettingsServiceImplTest',
  'com.aiinterview.platform.service.admin.impl.AdminPositionServiceImplTest',
  'com.aiinterview.platform.service.admin.impl.AdminUserServiceImplTest',
  'com.aiinterview.platform.service.admin.impl.TopCvImportServiceImplTest'
)

& "$PSScriptRoot\compile-and-run.ps1" -SourceList ($sources -join ',') -TestList ($tests -join ',')
exit $LASTEXITCODE
