# Contract: payOS Payment API

Base: `/api/v1/payment/payos`

## `POST /create` (authenticated)

```json
{ "amount": 1000, "description": "NAPTIEN" }
```

`200`:

```json
{ "success": true, "data": { "checkoutUrl": "https://pay.payos.vn/web/...", "orderCode": 1763936400000123, "qrCode": "..." } }
```

Errors: `400` (amount <= 0, chưa config, payOS từ chối), `401`.

## `POST /webhook` (public)

Payload:

```json
{
  "code": "00", "desc": "success", "success": true,
  "data": { "orderCode": 123, "amount": 3000, "code": "00", "paymentLinkId": "...", "reference": "..." },
  "signature": "<hmac-sha256-hex>"
}
```

Signature = HMAC-SHA256(checksumKey, sorted `data` as `key=value&...`).

Response `200`:

```json
{ "code": "00", "desc": "success", "success": true }
```

`code=01` for invalid payload/signature/order/amount; always HTTP 200 so payOS does not retry forever.

## `GET /return` (public)

Query: `code`, `id`, `cancel`, `status`, `orderCode`.

Response: `302 Location: ${app.frontend-url}/payment/payos/result?status=<success|pending|failed|error>&orderCode=<...>`

## `GET /cancel` (public)

Same query params; forces `cancel=true` → 302 `status=failed`.

## `GET /status?orderCode=...` (authenticated)

Đối soát với payOS; nếu payOS báo `PAID` thì cộng ví ngay (idempotent), kể cả khi webhook
hoặc return URL bị lỡ. Chỉ chủ giao dịch được xem.

`200`:

```json
{ "success": true, "data": { "orderCode": 1763936400000123, "status": "SUCCESS", "amount": 10000, "paymentLinkId": "..." } }
```

`status`: `PENDING | SUCCESS | FAILED`. Lỗi: `401` (chưa đăng nhập), `403` (không phải chủ giao dịch),
`404` (orderCode không tồn tại).

## Reconciliation

Scheduler `PayosServiceImpl.reconcilePendingPayments` chạy mỗi `PAYOS_RECONCILE_DELAY_MS`
(mặc định 120000 ms), quét tối đa `PAYOS_RECONCILE_BATCH_SIZE` giao dịch `PENDING` trong
`PAYOS_RECONCILE_LOOKBACK_HOURS` giờ gần nhất, query payOS và cộng ví nếu `PAID`.
