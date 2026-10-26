# Spec 011 — Ảnh đại diện + phiên đăng nhập

## Bối cảnh

Màn `/admin/account` (screen_22) có hai khối FE dán nhãn "Khối demo — BE chưa có API": tải/xóa ảnh
đại diện và danh sách "Thiết bị đăng nhập hoạt động". 24/10 anh Huy chốt **làm thật cả hai**
("cần việc BE"), không xóa UI. Số thứ tự 010 đã bị wave 2FA chiếm (`specs/010-two-factor-auth`)
nên wave này đánh số 011.

## Phạm vi đã chốt

1. **Ảnh = chuỗi data URL lưu trong DB, không có object storage.** BE không cấu hình
   `spring.servlet.multipart` và không có đĩa persist, nên `PUT /api/user/avatar` nhận nguyên
   `data:image/...;base64,...` và lưu vào `users.avatar` (LONGTEXT). Đổi lại: `UserDetailResponse`
   phát kèm cả ảnh (tới ~2.8 MB chuỗi), và mọi màn admin đọc user detail đều tải chuỗi đó.
2. **Một dòng `user_session` cho mỗi lần đăng nhập thành công**, UUID sinh ra nhét vào claim `sid`
   của **cả access lẫn refresh token**. Thu hồi phiên = ghi `revoked_at`, `JwtFilter` chặn ngay ở
   request kế tiếp thay vì đợi access token hết hạn (TTL cứng 24h).
3. **Token cũ không có `sid` vẫn sống nốt tới hết hạn.** `isLive(null) == true`: nếu chặn thì mọi
   phiên đang mở trước khi deploy sẽ bay hàng loạt mà không có cách tự cứu.
4. **Đăng xuất phải là một API.** `POST /api/v1/auth/logout` **thu hồi mọi phiên của tài khoản**
   (tokenVersion++ và `revokeAll(userId)`); FE chỉ xóa storage thì token vẫn gọi được API tới 24h.
   Thu hồi **từng thiết bị** là `DELETE /api/user/sessions/{id}`, không phải logout.

## Hợp đồng endpoint

| Method | Path | Quyền | Kết quả |
|---|---|---|---|
| POST | `/api/v1/auth/logout` | isAuthenticated | `tokenVersion++` + `revokeAll(userId)`: mọi access/refresh của tài khoản chết ngay, mọi dòng `user_session` ghi `revoked_at`; message "Đã đăng xuất mọi phiên" |
| GET | `/api/user/sessions` | isAuthenticated | danh sách phiên chưa thu hồi và còn hạn, `lastSeenAt` giảm dần, dòng của request này `current:true` |
| DELETE | `/api/user/sessions/{sessionId}` | isAuthenticated | id là **số dòng** trả từ GET; id không thuộc tài khoản -> 404 |
| PUT | `/api/user/avatar` | isAuthenticated | body `{dataUrl}`; hợp lệ -> lưu, sai -> 400 kèm `detail.dataUrl` |
| DELETE | `/api/user/avatar` | isAuthenticated | đặt `users.avatar = null` |

`UserDetailResponse` có thêm `avatar`. Login/refresh/google/github/sso và `verify-otp` (2FA) đều
phát hành `sid`; `refresh` mà token cũ không có `sid` thì mở phiên mới tại đó.

## Ràng buộc

- **Chặn ngay trong request ghi** (không lưu rồi mới lộ khi đọc): size <= 2 MB sau decode, và
  **magic bytes** phải là PNG/JPG/WEBP — BE không tin nhãn MIME hay phần đuôi tệp. Message trả về
  đúng các rule FE hiển thị: "Chỉ nhận ảnh PNG, JPG hoặc WEBP".
- `AvatarUpdateRequest` chặn thô ở tầng validate (`@Pattern` mime, `@Size(max = 2_800_000)`) để tệp
  10 MB không vào tới service.
- **Không bịa dữ liệu thiết bị:** `UserAgentParser.describe` chỉ suy ra trình duyệt + hệ điều hành
  từ User-Agent, màn hình hiển thị thêm IP; BE không có geo-IP nên không có tên thành phố.
- **`last_seen_at` ghi chậm nhất mỗi 5 phút** (`TOUCH_THROTTLE_MINUTES`) — request thường không
  được phát sinh write chỉ để cập nhật thời gian.
- **Mọi hành động cắt quyền phải thu hồi phiên:** đổi mật khẩu (`POST /api/user/change-password`),
  reset mật khẩu kèm "đăng xuất mọi thiết bị", admin khóa / vô hiệu hóa / xóa / đổi vai trò / đổi
  email của user đích — tất cả gọi `revokeAll(userId)`.
- `expires_at = phát hành + TTL refresh token` nên dòng tự hết hạn theo refresh, không cần job dọn.
- **Schema:** `ddl-auto: update`, Flyway tắt -> cột `users.avatar` và bảng `user_session` do entity
  sinh ra. `V6__user_session_and_avatar.sql` chỉ là tài liệu tham chiếu, không chạy.

## Sai lệch so với bản nháp (26/10, có chốt của anh Huy)

Bản nháp 011 được viết khi code 010 chưa vào `dev`. Khi rebase lên `origin/dev` (PR #31 đã merge),
hai điểm phải đổi:

1. **Logout là global, không phải per-device.** `dev` đã live hợp đồng `tokenVersion++`
   ("Đã đăng xuất mọi phiên", test `LOG-1` assert `users.token_version == 1`), còn bản nháp 011
   chỉ đóng đúng dòng `sid` của request — hai hành vi loại trừ nhau trên cùng một endpoint.
   Chốt: giữ logout global và **nối thêm** `revokeAll(userId)` để màn thiết bị + đường `/refresh`
   cùng thấy trạng thái thu hồi. Thu hồi từng thiết bị vẫn có, qua `DELETE /api/user/sessions/{id}`.
2. **Bảng chữ ký token là 4 tham số.** `JwtUtil.generateAccessToken/RefreshToken(email, role, tv, sid)`
   — 010 đã thêm claim `tv` vào access token, nên mọi nơi mint token (login, google, github, sso,
   `verify-otp`, `/refresh`) phải truyền `user.getTokenVersion()` chứ không để `null` như bản nháp.
   `JwtUtils.refreshAccessToken(refreshToken, tv, sid)` nhận sid từ caller vì caller là nơi quyết
   "giữ sid cũ / mint sid mới / chặn sid đã thu hồi".

Hệ quả phụ: `UserSessionService.revokeBySessionId` bị xóa — hết nơi gọi sau khi gộp logout, và nó
vốn đã không có caller trên `dev`.



`UserSessionAndAvatarIntegrationTest` (Testcontainers `mysql:8.0`, 8 case, chạy trên stack MySQL thật,
24/10: `Tests run: 8, Failures: 0, Errors: 0`) —
login sinh dòng + claim `sid` + `current:true`; logout giết token còn hạn; đổi mật khẩu thu hồi hết;
admin khóa user giết phiên của nạn nhân; thu hồi một thiết bị không ảnh hưởng thiết bị khác; id của
user khác -> 404; avatar round-trip qua DB và qua `GET /admin/users/{id}`; xóa avatar trả null;
ảnh GIF dán nhãn png / svg / rỗng / 2.9 MB -> 400 và cột vẫn null.

## Out of scope

- Ảnh đại diện cho **user khác** do admin chỉnh, và crop/resize: BE chỉ có avatar tự phục vụ.
- Màn `/admin/users/:id` vẫn còn `DemoTag` cho dot online / ngày sinh / nơi cư trú.
- Nút "Cấp độ bảo mật: 2FA Đã kích hoạt" ở header vẫn hardcode — FE của 2FA là wave riêng.
- Geo-IP, danh sách theo từng loại token, dọn dòng hết hạn bằng scheduler.
