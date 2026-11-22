# Contract: VNPay Payment API

Base path `/api/v1/payment/vnpay`. Success envelope `ApiResponse`; errors `ErrorModel` (standard). IPN returns VNPay-required JSON (not wrapped).

## 1. POST /api/v1/payment/vnpay/create — create payment URL

**Auth**: `isAuthenticated()` (Bearer JWT).

**Request body**:
```json
{ "amount": 100000, "orderInfo": "Nap tien vi" }
```
- `amount`: long, min 1 (VND).
- `orderInfo`: optional string, max 255; default "Nap tien vi".

**Response 200**:
```json
{
  "success": true,
  "data": {
    "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_...&vnp_SecureHash=...",
    "txnRef": "1732260000000-a1b2c3d4"
  }
}
```

**Errors**: 400 amount <= 0; 401 unauthenticated.

## 2. GET /api/v1/payment/vnpay/return — VNPay browser return

**Auth**: none (public). Query params = all VNPay return params (vnp_* including vnp_SecureHash).

**Behavior**: verify signature; update transaction; credit wallet on success (idempotent); redirect (302) to `{frontend-url}/payment/vnpay/result?status={success|failed|invalid-signature|error}&txnRef={txnRef}&responseCode={code}`.

**No JSON body** (redirect). On invalid signature redirect with status=invalid-signature.

## 3. GET (or POST) /api/v1/payment/vnpay/ipn — server-to-server

**Auth**: none (public). Query/body params = VNPay IPN params.

**Response 200** (always 200 with JSON, per VNPay spec):
```json
{ "RspCode": "00", "Message": "Confirm Success" }
```

**RspCode mapping**:
| Case | RspCode | Message |
|---|---|---|
| Success (incl. already confirmed) | 00 | Confirm Success |
| Unknown txnRef | 01 | Order not found |
| Amount mismatch | 04 | Invalid amount |
| Invalid signature | 97 | Invalid signature |
| Other error | 99 | Unknown error |