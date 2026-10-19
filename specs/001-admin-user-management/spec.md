# Feature Specification: Hoàn thành chức năng quản lý tài khoản người dùng (phía admin)

**Feature Branch**: `feature/mange-user`

**Created**: 2026-10-19

**Status**: Ready (đã clarify 2026-10-19)

**Input**: User description: "hoàn thành BE chức năng quản lý user"

## Bối cảnh (từ hiện trạng đã khảo sát)

Chức năng admin quản lý tài khoản **đã tồn tại 8 endpoint**: xem danh sách (phân trang + tìm kiếm +
lọc role/status), xem chi tiết, khóa, mở khóa, xóa mềm, gửi email đặt lại mật khẩu, đổi vai trò,
xem audit log. "Hoàn thành" vì vậy là **làm cho đúng hợp đồng các luồng đã có** và **bịt các lỗ hổng
đã đo được**, không phải viết mới từ đầu.

Lỗ hổng đã kiểm chứng bằng code (chi tiết ở báo cáo khảo sát ngày 2026-10-19):

1. Trạng thái mù: mở khóa được tài khoản **đã xóa**; xóa mềm rồi thì **không liệt kê được, không khôi phục được**, bộ lọc `DELETED` được quảng cáo nhưng không bao giờ trả kết quả.
2. Lỗi không nhất quán: cùng là "tài khoản đã xóa" chỗ trả 400, chỗ trả 404; giá trị lọc lạ thì trả danh sách rỗng thay vì bị chặn; phân trang âm/cực lớn nổ 500; vai trò sai kiểu nghi là 500.
3. Nuốt lỗi gửi mail: khóa/xóa/gửi link reset đều bắt lỗi mail rồi vẫn trả 200 "đã được gửi" — trái hợp đồng mail-fail-502 đã chốt ngày 2026-10-19.
4. Audit log có thể ghi admin giả (`adminId=0`, `adminEmail="system"`); hiển thị id thô, không lọc được theo admin/người bị tác động/hành động/thời gian.
5. Bảo vệ một tầng: toàn bộ route được phép đi qua tầng định tuyến, chỉ chặn bằng annotation ở controller; route admin không có lớp chặn thứ hai.
6. Token của tài khoản vừa bị khóa/vừa bị hạ vai trò vẫn dùng được tối đa 24h ở đường API (chỉ chặn được ở một bộ lọc đọc DB mỗi request).
7. Tìm kiếm không escape ký tự ẩn mẫu (`%`, `_`), phân trang không chặn cỡ trang lớn, kiểm đếm "còn admin" nạp cả bảng vào bộ nhớ.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Admin thao tác trên tài khoản mà trạng thái luôn đúng (Priority: P1)

Quản trị viên mở danh sách tài khoản, tìm một người, khóa/mở khóa/xóa/hạ - thăng vai trò. Hệ thống
từ chối mọi thao tác vô nghĩa (khóa người đã khóa, mở người chưa khóa, tác động lên người đã xóa,
tự tác động lên chính mình, để cơ quan trống quản trị) và luôn trả cùng một loại lỗi cho cùng một
tình huống, kèm lý do con người đọc được.

**Why this priority**: Đây là chức năng đã bán trong SRS (FR-AUTH-03 / FR-ADM-01). Trạng thái mù
khiến admin tin rằng đã mở khóa một tài khoản thực ra vẫn chết — hỏng niềm tin vào cả màn.

**Independent Test**: Với tài khoản ở từng trạng thái (đang hoạt động / đã khóa / đã xóa / đã tắt),
thử lần lượt từng thao tác và đối chiếu mã lỗi + trạng thái lưu + vết audit. Không cần làm thêm
tính năng mới nào.

**Acceptance Scenarios**:

1. **Given** tài khoản đã bị xóa mềm, **When** admin mở khóa/khóa/gửi link reset cho nó, **Then** request bị từ chối bằng cùng một mã lỗi như đường "đã xóa", tài khoản không đổi trạng thái, không có mail nào được gửi, vết audit cho nỗ lực đó nói rõ bị từ chối.
2. **Given** tài khoản đang bị khóa bởi admin, **When** admin mở khóa, **Then** tài khoản đăng nhập lại được và token cũ của nó không còn dùng được.
3. **Given** admin tự khóa / tự xóa / tự đổi vai trò chính mình, **When** gửi yêu cầu, **Then** bị từ chối 400 với message "không thể thao tác trên chính mình".
4. **Given** chỉ còn 1 tài khoản vai trò admin, **When** admin hạ vai trò hoặc xóa tài khoản đó, **Then** bị từ chối và hệ thống vẫn còn ≥1 admin.
5. **Given** một giá trị lọc vai trò/trạng thái không tồn tại, **When** xem danh sách, **Then** request bị chặn ở cửa vào với message nêu giá trị được phép, thay vì trả trang rỗng.
6. **Given** thông số phân trang âm hoặc cỡ trang vượt trần cho phép, **When** xem danh sách/audit log, **Then** bị chặn 400 trước khi chạm dữ liệu.
7. **Given** kênh mail đang hỏng (không gửi được), **When** admin khóa/xóa/gửi link reset, **Then** hệ thống trả 502 theo hợp đồng mail đã chốt (19/10) và **hoàn tác** thay đổi trạng thái + token vừa ghi; vết audit cho nỗ lực đó ghi rõ "đã hủy vì không gửi được mail". (Chốt 2026-10-19.)

---

### User Story 2 - Vòng đời tài khoản đầy đủ: tắt/bật, xóa, khôi phục (Priority: P2)

Quản trị viên tắt tạm một tài khoản (không cho đăng nhập nhưng giữ dữ liệu), bật lại, xóa mềm,
liệt kê nhóm đã xóa và khôi phục một tài khoản về trạng thái trước khi xóa.

**Why this priority**: Hiện chưa có đường nào bật/tắt trạng thái kích hoạt (chỉ email verify mới bật),
và xóa mềm là đường một chiều — admin không tìm lại và không cứu được tài khoản xóa nhầm.
Mockup admin đã có bộ lọc trạng thái `DELETED`.

**Independent Test**: Tạo tài khoản, tắt → thử đăng nhập thất bại với message "chưa kích hoạt", bật →
đăng nhập được; xóa → xuất hiện trong danh sách "đã xóa", khôi phục → biến khỏi danh sách đó và
đăng nhập lại được theo trạng thái khóa/kích hoạt trước khi xóa.

**Acceptance Scenarios**:

1. **Given** tài khoản đang bật, **When** admin tắt, **Then** chủ tài khoản không đăng nhập được, message phân biệt được với "bị khóa"; bật lại thì vào bình thường.
2. **Given** tài khoản đã xóa mềm, **When** admin mở danh sách với bộ lọc "đã xóa", **Then** thấy nó kèm ai xóa / xóa lúc nào; **When** admin khôi phục, **Then** tài khoản trở về đúng trạng thái khóa/kích hoạt trước khi xóa.
3. **Given** admin khôi phục rồi xóa lại liên tiếp nhiều lần, **When** xem lịch sử, **Then** mỗi lần đều có vết audit riêng, không đè lên nhau.
4. **Given** xem chi tiết tài khoản, **Then** admin thấy đủ thông tin ra quyết định: số điện thoại, trạng thái kích hoạt/khóa/xóa, còn phiên hiệu lực hay không. Mặc định (chưa được anh Huy chốt riêng): admin thấy **hai tín hiệu khóa tách nhau** — "bị admin khóa" và "đang bị chặn tạm vì đăng nhập sai N lần tới thời điểm X" — suy từ trạng thái hiện có, không đổi schema.

---

### User Story 3 - Audit log tra cứu được, không có số giả (Priority: P2)

Quản trị viên mở lịch sử thao tác, lọc theo người thực hiện, người bị tác động, loại hành động,
khoảng thời gian; mỗi dòng cho biết "ai — nhắm vào ai — làm gì — kết quả". Nỗ lực thất bại cũng
được ghi vết.

**Why this priority**: Audit log là bằng chứng duy nhất của thao tác quản trị. Hiện nó chứa bản ghi
admin giả (`0` / `"system"`), chỉ có id thô của nạn nhân, không lọc được, và không ghi nỗ lực bị từ
chối — tức là không dùng được để tra cứu khi có sự cố.

**Independent Test**: Thực hiện chuỗi thao tác hợp lệ + không hợp lệ, rồi mở lịch sử với từng bộ lọc
và đối chiếu từng dòng.

**Acceptance Scenarios**:

1. **Given** một thao tác admin, **When** xem lịch sử, **Then** dòng đó mang id + email admin thật đã thực hiện, không bao giờ là `0`/`system`.
2. **Given** nhiều hành động đã ghi, **When** lọc theo người bị tác động hoặc theo loại hành động hoặc khoảng ngày, **Then** chỉ nhận đúng các dòng thỏa mãn, vẫn phân trang.
3. **Given** thao tác bị từ chối (đã khóa, tự tác động, admin cuối cùng), **When** xem lịch sử, **Then** thấy cả nỗ lực thất bại kèm lý do — mỗi từ chối 1 dòng, có cờ kết quả thành công/từ chối. (Chốt 2026-10-19.)
4. **Given** lịch sử hiển thị cho màn admin, **Then** mỗi dòng cho biết email người bị tác động, không chỉ id.

---

### User Story 4 - không phá vỡ các luồng khác & dữ liệu nhạy cảm an toàn (Priority: P3)

Các màn đang dùng API (đăng nhập, quên mật khẩu phía người dùng, ứng tuyển, gói dịch vụ) giữ nguyên
hành vi; phản hồi lỗi không rò rỉ chi tiết nội bộ; không endpoint nào để lộ mật khẩu/token; tài khoản
bị khóa/xóa/tắt thì token đang phát hành phải chết theo.

**Why this priority**: Đây là điều kiện để merge, không phải tính năng bán cho admin, nhưng repo này
đã từng vỡ vì đổi hành vi mà không đối chiếu luồng khác.

**Independent Test**: Chạy lại bộ đo hiện có (register/login/forgot-password + danh sách admin) sau khi sửa, so số trước/sau.

**Acceptance Scenarios**:

1. **Given** token cấp trước khi tài khoản bị khóa/xóa/tắt/hạ vai trò, **When** gọi API bằng token đó, **Then** bị từ chối trong vòng một lần dùng tiếp theo, không phải sau 24h.
2. **Given** request sai đủ loại (thiếu quyền, thân lỗi, tham số lỗi), **When** nhận phản hồi, **Then** message là nghiệp vụ, không chứa chi tiết nội bộ của hệ thống.
3. **Given** vai trò nhân viên vận hành (Staff), **When** gọi các endpoint quản lý tài khoản, **Then** nhận 403 — quản lý tài khoản **chỉ dành cho ROLE_ADMIN** (chốt 2026-10-19, chốt luôn Q-12 của SRS cho mảng này).

---

### Edge Cases

- Tài khoản vừa bị admin khóa, vừa đang trong đồng hồ khóa tự động vì đăng nhập sai → mở khóa xử lý thế nào cho cả hai?
- Người dùng đang chờ link reset của mình, admin gửi link reset khác → link cũ có chết không, có cảnh báo nào không? (hiện: ghi đè âm thầm)
- Khôi phục tài khoản đã xóa mà email của nó đã bị một tài khoản khác chiếm (xóa mềm vẫn giữ email unique) → xử lý xung đột thế nào?
- Lọc `search` chứa `%`, `_`, khoảng trắng hai đầu, chuỗi rỗng.
- Cỡ trang vượt số bản ghi thật; trang vượt tổng số trang (âm và dương).
- Admin thao tác lên chính tài khoản admin khác (không phải mình) — được phép, nhưng phải còn ≥1 admin sau thao tác.
- Kênh mail timeout giữa lúc đang đổi trạng thái (hợp đồng 502/rollback mới áp dụng cho luồng nào?).
- Token đang hiệu lực của tài khoản bị tắt (enabled=false) — bộ lọc hiện chỉ chặn khi `!enabled || locked || deletedAt`, cần đo lại cho đủ 3 trạng thái.

## Requirements *(mandatory)*

### Functional Requirements

**A. Sửa hợp đồng luồng đã có (P1)**

- **FR-001**: System MUST từ chối mọi thao tác quản trị (khóa, mở khóa, xóa, khôi phục, gửi link reset, đổi vai trò) trên tài khoản đã xóa mềm, với cùng một mã lỗi và cùng một message cho mọi thao tác.
- **FR-002**: System MUST chặn giá trị lọc vai trò/trạng thái không thuộc danh mục được phép ngay ở cửa vào (400), kèm message liệt kê giá trị hợp lệ.
- **FR-003**: System MUST chặn phân trang không hợp lệ (trang âm, cỡ trang ≤ 0 hoặc vượt trần, giả định trần 100) với 400 trước khi truy vấn.
- **FR-004**: System MUST không trả thông tin nội bộ của hệ thống trong message lỗi 500; lỗi không được chủ động ném phải thành message nghiệp vụ.
- **FR-005**: System MUST áp hợp đồng mail-fail cho thao tác admin: không gửi được mail thì trả 502 và hoàn tác thay đổi trạng thái/token vừa ghi (chốt 2026-10-19), và message trả về phải mô tả đúng những gì đã xảy ra (không khẳng định "đã gửi" khi chưa gửi).
- **FR-006**: System MUST escape ký tự ẩn mẫu trong ô tìm kiếm, và đặt giới hạn độ dài chuỗi tìm kiếm.
- **FR-007**: System MUST kiểm đếm "còn ít nhất một admin" bằng phép đếm, không nạp danh sách; guard này áp cho cả đường xóa và đường hạ vai trò.
- **FR-008**: System MUST dùng một TTL duy nhất cho link đặt lại mật khẩu giữa đường admin gửi và đường người dùng tự yêu cầu, và nội dung email phải khớp TTL đó (giả định: theo luồng người dùng = 30 phút, nếu anh không đổi).

**B. Vòng đời tài khoản (P2)**

- **FR-009**: System MUST cho admin bật/tắt trạng thái kích hoạt của tài khoản; tắt thì chủ tài khoản không đăng nhập được và message đăng nhập phân biệt được "bị tắt/bị khóa/bị xóa/chưa xác thực email".
- **FR-010**: System MUST cho admin liệt kê tài khoản đã xóa mềm và khôi phục chúng; khôi phục trả tài khoản về trạng thái khóa/kích hoạt gần nhất trước khi xóa.
- **FR-011**: System MUST hiển thị trong thông tin chi tiết: số điện thoại, trạng thái kích hoạt, trạng thái khóa (kèm ai khóa và lúc nào, phân biệt khóa bởi admin và khóa tự động vì đăng nhập sai), thời điểm xóa nếu đã xóa.
- **FR-012**: System MUST thu hồi ngay hiệu lực đăng nhập của tài khoản bị khóa/tắt/xóa/hạ vai trò (không cho phép token đã phát hành tiếp tục dùng).

**C. Audit log (P2)**

- **FR-013**: System MUST ghi audit với admin thật; nếu không xác định được người thực hiện thì từ chối thao tác (hoặc ghi theo cơ chế khác), không được ghi `0`/`"system"` giả.
- **FR-014**: System MUST cho lọc lịch sử thao tác theo: admin thực hiện, tài khoản bị tác động, loại hành động, khoảng thời gian; kết quả phân trang, sắp xếp giảm dần theo thời điểm.
- **FR-015**: System MUST trả trong mỗi dòng lịch sử email (và tên) của tài khoản bị tác động, không chỉ id.
- **FR-016**: Hành động audit phải là tập giá trị đóng (danh mục cố định), không phải chuỗi tự do.

**D. Phòng vệ nhiều tầng (P3)**

- **FR-017**: System MUST chặn mọi đường dẫn quản trị bằng cả hai lớp: quy tắc định tuyến theo vai trò AND annotation kiểm quyền; thiếu một lớp vẫn còn lớp kia.
- **FR-018**: System MUST giữ hành vi các endpoint không thuộc phạm vi này (đăng ký, đăng nhập, quên mật khẩu phía người dùng, tin tuyển dụng, hồ sơ, gói dịch vụ) — đo lại trước/sau.

### Key Entities

- **Tài khoản người dùng**: email, tên, số điện thoại, vai trò (4 giá trị hiện có), cờ kích hoạt, cờ khóa bởi admin, đồng hồ khóa tự động (số lần sai + thời điểm mở khóa), thời điểm xóa mềm, phiên token hiệu lực.
- **Vết audit quản trị**: ai thực hiện, nhắm vào tài khoản nào, hành động thuộc danh mục nào, chi tiết trước→sau, kết quả (thành công/từ chối), thời điểm.
- **Danh mục trạng thái tài khoản** cho bộ lọc: đang hoạt động / chưa kích hoạt / bị khóa / đã xóa mềm — mỗi giá trị phải đếm được và lọc được thật.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% cặp (thao tác × trạng thái tài khoản) trong bảng ma trận 6 thao tác × 4 trạng thái trả đúng mã lỗi và message đã chốt; không tổ hợp nào rơi vào 500.
- **SC-002**: Sau khi khóa/tắt/xóa/hạ vai trò, token đã phát hành của tài khoản đó bị từ chối ở request kế tiếp (đo bằng curl), không chờ hết hạn.
- **SC-003**: Admin tìm được tài khoản đã xóa mềm và khôi phục nó thành công trong ≤ 2 thao tác, và trạng thái sau khôi phục khớp trạng thái trước xóa.
- **SC-004**: Mỗi dòng lịch sử thao tác đều trỏ được tới admin thật và tài khoản bị tác động bằng email; lọc theo từng tiêu chí trả đúng tập kết quả (đối chiếu SQL).
- **SC-005**: Không message lỗi nào của endpoint quản trị chứa chi tiết nội bộ hệ thống; bộ đo cũ (register/login/forgot) cho kết quả trước/sau như nhau.
- **SC-006**: Mọi số liệu trong mục "Cách đã verify" là số đo thật từ app chạy thật (Docker/staging), không dựa test container trên máy local.

## Phạm vi đã chốt (clarify 2026-10-19)

- **Làm**: P1 (siết hợp đồng 8 endpoint hiện có) + P2 (vòng đời bật/tắt, khôi phục, audit tra cứu được).
- **Không làm lần này**: dashboard thống kê (FR-ADM-03), bulk action, API admin tạo tài khoản,
  hard delete / anonymize dữ liệu liên quan, endpoint danh sách phiên đăng nhập.
- **Mail fail trong thao tác admin**: 502 + hoàn tác trạng thái/token vừa ghi (theo hợp đồng 19/10).
- **Quyền**: quản lý tài khoản chỉ ROLE_ADMIN; ROLE_STAFF nhận 403 (chốt Q-12 cho mảng này).
- **Audit**: ghi cả nỗ lực bị từ chối (kèm lý do + cờ kết quả); mỗi dòng trả email/tên nạn nhân.
- **Hiển thị khóa** (mặc định, chưa chốt riêng): tách hai tín hiệu "admin khóa" và "chặn tạm vì đăng
  nhập sai" từ trạng thái hiện có, không đổi schema.

## Assumptions

- Trần cỡ trang mặc định 100; trang mặc định 0, cỡ trang mặc định 20 (giữ như hiện tại).
- "Quản lý user" ở đây = phía admin. Màn hồ sơ của chính người dùng (`/api/user/profile` cũ) chỉ được xử lý ở mức gỡ/ sửa cho đúng chuẩn nếu cần, không mở rộng.
- Không đổi cơ chế đăng nhập/JWT hiện có, chỉ thêm bước thu hồi hiệu lực phiên khi trạng thái tài khoản thay đổi.
- Không làm dashboard thống kê (FR-ADM-03), không làm quản lý tin tuyển dụng (FR-ADM-02), không hard-delete vật lý, không xóa dữ liệu liên quan (GDPR anonymize) — ngoài phạm vi lần này.
- Schema vẫn tiến hoá bằng `ddl-auto: update` (Flyway đang tắt); nếu phải thêm cột/index thì ghi rõ trong plan.
- Role hiện có 4 giá trị (`ROLE_USER`, `ROLE_STAFF`, `ROLE_ADMIN`, `ROLE_INTERVIEWER`), không có super-admin.
- Ngôn ngữ: message nghiệp vụ tiếng Việt, chrome tiếng Anh theo quyết định đã chốt.
