# Quickstart: payOS nạp ví

> payOS hiện **không có sandbox riêng**. Bạn cần tài khoản tại https://my.payos.vn, xác thực CCCD, liên kết tài khoản ngân hàng, rồi tạo kênh thanh toán để lấy 3 key. Tham khảo: https://payos.vn/docs/moi-truong-test

## 1. Lấy credential

Vào `my.payos.vn` → tạo/xác thực kênh thanh toán → copy:

- `Client ID`
- `API Key`
- `Checksum Key`

## 2. Cấu hình backend

```bash
PAYOS_CLIENT_ID=<client id>
PAYOS_API_KEY=<api key>
PAYOS_CHECKSUM_KEY=<checksum key>
# URL public của BACKEND (không phải FE). Trên Render có thể bỏ trống:
# app tự dùng RENDER_EXTERNAL_URL để dựng return/cancel URL.
APP_PUBLIC_BASE_URL=https://<backend-domain>
PAYOS_RETURN_URL=https://<backend-domain>/api/v1/payment/payos/return
PAYOS_CANCEL_URL=https://<backend-domain>/api/v1/payment/payos/cancel
```

> **Không đặt `PAYOS_RETURN_URL` thành `localhost` khi đã deploy.** Nếu biến này còn sót
> localhost (hoặc trỏ về FE), backend sẽ tự thay bằng `APP_PUBLIC_BASE_URL` / `RENDER_EXTERNAL_URL`.
> FE gọi `GET /api/v1/payment/payos/status?orderCode=...` sau khi redirect để đối soát và cộng ví,
> nên giao dịch vẫn về đúng dù webhook bị lỡ. Job reconcile mặc định chạy mỗi 2 phút cho các txn `PENDING`.

Đăng ký webhook public (ngrok cho local) trong payOS dashboard hoặc gọi:

```bash
POST https://api-merchant.payos.vn/confirm-webhook
x-client-id: <client id>
x-api-key: <api key>
Content-Type: application/json

{"webhookUrl":"https://<domain>/api/v1/payment/payos/webhook"}
```

## 3. Tạo link

```bash
curl -X POST http://localhost:8080/api/v1/payment/payos/create \
  -H "Authorization: Bearer <JWT>" -H "Content-Type: application/json" \
  -d '{"amount":1000,"description":"NAPTIEN"}'
```

Response:

```json
{"success":true,"data":{"checkoutUrl":"https://pay.payos.vn/web/...","orderCode":1763936400000123,"qrCode":"..."}}
```

Mở `checkoutUrl`, quét QR / chuyển khoản với nội dung payOS hiển thị.

## 4. Webhook, return & đối soát

- `POST /api/v1/payment/payos/webhook` → verify signature → cộng ví idempotent.
- `GET /api/v1/payment/payos/return?code=00&id=...&status=PAID&orderCode=...` → query payOS → 302 về frontend `?status=success&orderCode=...`.
- `GET /api/v1/payment/payos/status?orderCode=...` (JWT) → query payOS; nếu `PAID` thì cộng ví ngay,
  trả `{ status: PENDING|SUCCESS|FAILED, amount, ... }`. FE dùng endpoint này thay vì tin query param.
- Scheduler `reconcilePendingPayments` quét txn `PENDING` trong 24h gần nhất mỗi 2 phút.
- Gọi lại webhook/status nhiều lần → số dư không đổi (idempotency key `PAYOS-<orderCode>` + khoá row).

## 5. Kiểm tra ví

```bash
curl http://localhost:8080/api/v1/wallet -H "Authorization: Bearer <JWT>"
```
