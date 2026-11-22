# Contract: ZaloPay Payment API

Base: `/api/v1/payment/zalopay`

## `POST /create` (authenticated)

Request:

```json
{ "amount": 1000, "orderInfo": "Nap tien vi" }
```

Response `200`:

```json
{ "success": true, "data": { "paymentUrl": "https://qcgateway.zalopay.vn/openinapp?order=...", "appTransId": "261122_xxxxxxxxxxxx" } }
```

Errors: `400` amount <= 0 / gateway failure; `401` unauthenticated.

## `POST /callback` (public)

Request:

```json
{ "data": "{\"app_id\":554,\"app_trans_id\":\"...\",\"amount\":1000,\"zp_trans_id\":\"...\",\"channel\":\"...\"}", "mac": "<hex>", "type": 1 }
```

Response `200` (always JSON, never redirect):

```json
{ "return_code": 1, "return_message": "success" }
```

| return_code | meaning |
|---|---|
| `1` | confirmed / already confirmed |
| `-1` | bad payload, bad MAC, unknown order, amount mismatch |
| `0` | retryable exception (ZaloPay retries) |

## `GET /return` (public)

Query params from ZaloPay: `appid`, `apptransid`, `pmcid`, `bankcode`, `amount`, `discountamount`, `status`, `checksum`.

Response: `302 Location: ${app.frontend-url}/payment/zalopay/result?status=<...>&appTransId=<...>`

Status values: `success`, `pending`, `failed`, `invalid-signature`, `error`.
