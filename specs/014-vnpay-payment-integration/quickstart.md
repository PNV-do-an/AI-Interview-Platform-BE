# Quickstart: VNPay Payment Gateway Integration

## Prerequisites

- App running (local or Docker). MySQL available.
- Env vars set:
  - `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET` (sandbox credentials from VNPay merchant portal)
  - Optional: `VNPAY_PAY_URL` (default sandbox), `VNPAY_RETURN_URL`, `VNPAY_IPN_URL`, `FRONTEND_URL`.
- A registered user with JWT token.

## 1. Create payment URL

```bash
curl -X POST http://localhost:8080/api/v1/payment/vnpay/create \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"amount":100000,"orderInfo":"Nap tien vi"}'
```

Expected: 200 with `data.paymentUrl` and `data.txnRef`. Open paymentUrl in browser to pay in sandbox (use VNPay test card).

## 2. Verify return (after paying in sandbox)

VNPay redirects to `/api/v1/payment/vnpay/return?...`. Check browser lands on frontend result page with `status=success`. Check wallet balance increased:

```bash
curl http://localhost:8080/api/v1/wallet/me -H "Authorization: Bearer $TOKEN"
```

## 3. IPN replay test (idempotency)

Re-send the same IPN query (copy from server logs / VNPay dashboard) to `/api/v1/payment/vnpay/ipn`. Expected RspCode=00 and wallet balance unchanged.

## 4. Invalid signature test

Modify one param in a valid return/IPN URL. Expected: return redirects status=invalid-signature; IPN returns RspCode=97. No credit.

## 5. Amount mismatch test

IPN with vnp_Amount different from stored. Expected RspCode=04. No credit.

## 6. Unknown txnRef test

IPN with unknown vnp_TxnRef. Expected RspCode=01.