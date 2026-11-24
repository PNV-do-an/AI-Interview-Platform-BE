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
PAYOS_RETURN_URL=http://localhost:8080/api/v1/payment/payos/return
PAYOS_CANCEL_URL=http://localhost:8080/api/v1/payment/payos/cancel
```

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

## 4. Webhook & return

- `POST /api/v1/payment/payos/webhook` → verify signature → cộng ví idempotent.
- `GET /api/v1/payment/payos/return?code=00&id=...&status=PAID&orderCode=...` → query payOS → 302 về frontend `?status=success&orderCode=...`.
- Gọi lại webhook nhiều lần → số dư không đổi.

## 5. Kiểm tra ví

```bash
curl http://localhost:8080/api/v1/wallet -H "Authorization: Bearer <JWT>"
```
