# Phase 0 Research: VNPay Payment Gateway Integration

**Date**: 2026-11-22 | **Plan**: [plan.md](./plan.md)

## R1. VNPay payment URL construction

- **Decision**: Use VNPay v2.1.0 payment URL flow. Required params: `vnp_Version=2.1.0`, `vnp_Command=pay`, `vnp_TmnCode`, `vnp_Amount` (VND * 100), `vnp_CurrCode=VND`, `vnp_TxnRef`, `vnp_OrderInfo`, `vnp_OrderType=other`, `vnp_Locale=vn`, `vnp_ReturnUrl`, `vnp_IpAddr`, `vnp_CreateDate`, `vnp_ExpireDate`. Sort keys alphabetically, join as `k=v` with `&`, HMAC-SHA512 with secret, append as `vnp_SecureHash`. Build final URL `{payUrl}?{queryString}&vnp_SecureHash={hash}`.
- **Rationale**: Standard VNPay spec; matches merchant portal sandbox.
- **Alternatives**: Use VNPay official SDK (Java) — rejected: adds dependency, simple enough to implement directly; keeps control over encoding/timezone.

## R2. Timezone for CreateDate/ExpireDate

- **Decision**: Use `Asia/Ho_Chi_Minh` (GMT+7) formatted `yyyyMMddHHmmss`. ExpireDate = CreateDate + 15 minutes.
- **Rationale**: VNPay validates against server time; sandbox runs GMT+7.
- **Alternatives**: UTC — rejected: VNPay sandbox expects local time.

## R3. Signature verification

- **Decision**: Extract all `vnp_*` params except `vnp_SecureHash` and `vnp_SecureHashType`, sort, join `k=v` with `&`, compute HMAC-SHA512, compare case-insensitively with provided hash. Use constant-time comparison (`MessageDigest.isEqual` on bytes) to avoid timing attacks.
- **Rationale**: VNPay spec; case-insensitive hash comparison is conventional.
- **Alternatives**: Standard `String.equals` — rejected: timing side-channel (minor but cheap to fix).

## R4. Idempotency of credit

- **Decision**: Use `WalletService.creditAvailable(..., idempotencyKey=txnRef)` for VNPAY top-up; additionally guard `VnpayTransaction.status`: only credit when status is PENDING, then set SUCCESS. Double-check status before credit under DB transaction (row lock via `findByTxnRefForUpdate` or rely on idempotency key unique constraint).
- **Rationale**: Two independent guards (status + idempotency key) make double credit impossible even under races.
- **Alternatives**: Only status guard — rejected: race window; only idempotency key — rejected: doesn't track VNPay-specific state.

## R5. Return vs IPN responsibility

- **Decision**: Both endpoints perform verification + idempotent credit. Return redirects browser to frontend result page (`{frontend-url}/payment/vnpay/result?status=...&txnRef=...`). IPN returns VNPay-required JSON `{"RspCode":"..","Message":".."}`. IPN is authoritative for production; return is UX.
- **Rationale**: VNPay docs state IPN is mandatory; return may be skipped by user.
- **Alternatives**: Credit only in IPN — rejected: return gives faster UX feedback; idempotency makes both safe.

## R6. IPN response codes

- **Decision**: Map outcomes to VNPay RspCode: 00 success (including already confirmed), 01 order not found, 04 invalid amount, 97 invalid signature, 99 other error. Message strings per VNPay spec ("Confirm Success", "Order not found", "Invalid amount", "Invalid signature", "Unknown error").
- **Rationale**: VNPay merchant portal expects these exact codes.
- **Alternatives**: Generic 99 for all — rejected: portal shows specific failure reasons.

## R7. Auth on public endpoints

- **Decision**: `/api/v1/payment/vnpay/return` and `/api/v1/payment/vnpay/ipn` are unauthenticated (no `@PreAuthorize`). Security config already `anyRequest().permitAll()` with `@PreAuthorize` on create only. Create uses `@PreAuthorize("isAuthenticated()")`.
- **Rationale**: VNPay calls IPN server-to-server without our JWT; browser return has no auth context reliably.
- **Alternatives**: Signed token in return URL — rejected: VNPay controls return URL params; cannot add our token.

## R8. Transaction entity design

- **Decision**: New `VnpayTransaction` with status enum PENDING/SUCCESS/FAILED, unique `txnRef`, FK-ish `userId` (Long, not join, to avoid lazy issues in public endpoints), amount (VND), responseCode, vnpTransactionNo, bankCode, payDate, orderInfo, createdAt/updatedAt. Add `VNPAY_TOPUP` to `WalletTxType`.
- **Rationale**: Tracks VNPay-specific fields; keeps wallet ledger reference via idempotency key.
- **Alternatives**: Reuse `WalletTransaction` only — rejected: loses VNPay response details needed for reconciliation.

## R9. Config

- **Decision**: Add `app.vnpay.*` to application.yml: `tmn-code`, `hash-secret`, `pay-url` (default sandbox), `return-url`, `ipn-url`, `version` (2.1.0), `command` (pay), `order-type` (other), `locale` (vn), `currency` (VND), `expire-minutes` (15). Override via env vars VNPAY_TMN_CODE, VNPAY_HASH_SECRET, VNPAY_PAY_URL, VNPAY_RETURN_URL, VNPAY_IPN_URL.
- **Rationale**: Matches existing config pattern (env with defaults).
- **Alternatives**: Separate profile — rejected: env override is sufficient.

## NEEDS CLARIFICATION

None. All defaults chosen per VNPay spec and existing wallet patterns.