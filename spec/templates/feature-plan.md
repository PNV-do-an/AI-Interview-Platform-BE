# <Tên tính năng>

Trạng thái: `draft` | `ready` | `in-progress` | `done` | `blocked`
Người/phiên tạo: <ngày, branch>
Mockup nguồn: `C:\Users\huy\Downloads\.stitch_extracts\latest_3\stitch_ai_interview_platform_ui_ux\<ten-man>\` (hoặc "không có mockup")

## 1. Mục tiêu

Một câu: người dùng nào làm được gì sau khi xong. Không viết giải pháp ở đây.

## 2. Phạm vi / Không làm

- Làm: ...
- Không làm: ... (kể cả thứ mockup có nhưng mình quyết bỏ)

## 3. API thật dùng được

| Endpoint | Method | Response liên quan | Ghi chú |
|---|---|---|---|
| `/api/v1/...` | GET | `data.<field>` | đã verify bằng curl ngày ... |

Không có endpoint nào phía trên mà UI vẫn cần -> ghi vào mục 6, đừng bịa số liệu.

## 4. Ánh xạ số liệu -> UI

| Thành phần mockup | Nguồn | Xử lý khi trống |
|---|---|---|
| Hero "Welcome back, X" | `user.fullName` | không có case trống |
| Metric "Latest AI Score" | `sessions[i].overallScore` của session COMPLETED gần nhất | ẩn card, không hiện `0` |

## 5. Khối mockup BỎ + lý do

- "<tên khối>" — thiếu `<field/endpoint>`; dẫn chứng: `models/<X>.model.ts` không có field.

## 6. Việc backend cần làm (nếu có)

- Bảng/column thiếu: ...
- Endpoint cần thêm: `POST /api/v1/...` request/response mong muốn: ...
- Rule nghiệp vụ phải chặn **lúc ghi** (422 ở request tạo/cập nhật), không để lưu rồi mới lộ khi GET.

## 7. Test cases

| # | Tình huống | Kết quả mong đợi |
|---|---|---|
| 1 | Chưa có đơn ứng tuyển | empty state, không có bảng rỗng |
| 2 | Slot đã bị huỷ | không hiện ở Next Interview |

## 8. Cách đã verify

Chạy theo `spec/verification.md`. Ghi rõ: route nào đã mở thật bằng vai trò nào, route nào chỉ smoke render, phần nào chưa xem được vì màn hình desktop trống.

## 9. Câu hỏi mở cho anh Huy

- ... (mỗi câu có đáp án đề xuất, để chốt nhanh)
