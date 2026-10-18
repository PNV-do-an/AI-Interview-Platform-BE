# Spec — phần backend

Đọc ở đây trước khi mở code khi nhận một yêu cầu tính năng.

Repo này chỉ giữ các tài liệu nghiêng về API / dữ liệu / kiểm chứng nghiệp vụ.
Giao diện và ánh xạ mockup nằm ở repo `AI-Interview-Platform-FE` (`spec/` của repo đó).
**Bản đầy đủ của mọi tài liệu (cả hai mảng) nằm ở superrepo
`AI-Interview-Platform-deploy-staging`, thư mục `spec/`** — đó là nơi để sửa;
bản sao ở đây để đọc theo mảng.

## Cấu trúc

```
spec/
  README.md                      file này
  srs-core-baseline.md           SRS Core Baseline (3 vai trò) -> đối chiếu từng FR/NFR với code thật
  known-gaps.md                  lỗ hổng backend/API/số liệu đã kiểm chứng, chưa sửa
  features/sign-up.md            kế hoạch luồng đăng ký (API, rate-limit, test cases, số đo)
  features/password-reset.md     kế hoạch luồng quên mật khẩu (API, thu hồi phiên, số đo)
  templates/feature-plan.md      mẫu kế hoạch cho tính năng mới
```

## Ở repo khác

| Tài liệu | Nằm ở |
|---|---|
| `INDEX.md` (29 màn Stitch → route → file → trạng thái) | FE |
| `conventions/frontend-ui.md` (luật giao diện) | FE |
| `verification.md` (type, cache, soát DOM bằng browser-use) | FE |
| `features/mockup-parity-next.md` (parity còn lại với mockup) | FE |

## Cách dùng khi code một thứ mới

1. Copy `templates/feature-plan.md` thành `spec/features/<ten-tinh-nang>.md` trong superrepo.
2. Ghi rõ hợp đồng API (endpoint, status, message) trước, rồi mới tới code.
3. Mục "Cách đã verify" bắt buộc có số đo thật (curl/DOM/SQL), không ghi "đã chạy".
4. Test backend: `mvn -B clean test` (bắt buộc có `clean`). `PersistenceTest` và
   `AdminUserControllerIntegrationTest` dùng Testcontainers nên **chỉ đo thật trên CI** —
   trên Windows hai lớp container không bắt được nhau.
