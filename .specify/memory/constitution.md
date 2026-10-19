# AI Interview Platform BE Constitution

**Version**: 1.0
**Ratified**: 2026-10-19
**Last Amended**: 2026-10-19

## Core Principles

### I. Hợp đồng API trước, code sau
Mỗi tính năng phải được ghi hợp đồng hành vi (endpoint, request, status, message, rule nghiệp vụ)
vào `spec/features/<ten>.md` theo `spec/templates/feature-plan.md` TRƯỚC khi viết code.
Tài liệu gốc nằm ở superrepo `AI-Interview-Platform-deploy-staging/spec/`; bản sao trong repo này để đọc theo mảng.

### II. Số đo thật (NON-NEGOTIABLE)
Mục "Cách đã verify" bắt buộc có số đo thật (curl / SQL / DOM). Cấm ghi "đã chạy", "đã test".
Test chỉ xanh trên CI (Testcontainers không chạy được trên Windows local) không được coi là đã verify;
muốn khẳng định phải chạy app thật (Docker/Render) và đo.

### III. Chặn lúc ghi
Mọi rule nghiệp vụ phải trả lỗi (400/422) ngay ở request ghi. Không để dữ liệu sai được lưu rồi
mới lộ ra khi GET. Validation ở DTO chỉ giữ rule format; rule nghiệp vụ nằm ở service.

### IV. Audit thật cho thao tác quản trị
Mỗi hành động admin phải ghi `admin_audit_log` với admin thật (không `adminId=0`/"system" giả),
target, action, detail. Thao tác phải nhất quán một chiều trạng thái (không cho phép thao tác
lên tài khoản đã xóa / đã khóa rồi mà không có ý nghĩa nghiệp vụ).

### V. Hợp đồng lỗi nhất quán
Response thành công dùng envelope `ApiResponse`; lỗi dùng `ErrorModel` và cùng một quy ước trên
mọi endpoint. Mail fail theo hợp đồng hiện tại là 502 + hoàn tác — không nuốt lỗi rồi trả 200
với message khẳng định "đã gửi". Message nghiệp vụ tiếng Việt, chrome tiếng Anh theo mockup.

### VI. Phòng vệ nhiều tầng
Luồng quản trị phải được chặn cả ở tầng URL (security config) lẫn `@PreAuthorize` ở service,
không dựa vào một lớp duy nhất. Token của tài khoản khóa/xóa phải bị từ chối tức thời.

### VII. Phạm vi tối thiểu
Chỉ làm đúng phần được yêu cầu; không mở rộng sang migration, UI, hay tính năng láng giềng
khi chưa được chốt. Không đưa số liệu ước lượng thời gian vào kế hoạch/báo cáo.

### VIII. Kiểm chứng trước workaround
Trước khi đề xuất phương án vòng ngoài (cache, job tay, đổi schema), phải chạy lại code production
và đo số hiện tại. Không xóa guard ở biên chỉ vì dữ liệu hiện tại hợp lệ.

## Governance

- Nguyên tắc này ưu tiên hơn thói quen cá nhân trong session; thay đổi phải có lý do ghi ở PR.
- Rule mới phải khả thi kiểm chứng; thêm/v/sửa/xóa phải cập nhật version và ngày ở header.
- Deviations cần sự đồng ý của chủ repo (anh Huy) và phải được ghi chú trong spec của feature.
