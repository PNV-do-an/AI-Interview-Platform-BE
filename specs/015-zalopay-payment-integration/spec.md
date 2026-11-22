# Feature Specification: ZaloPay Payment Gateway Integration

**Feature Branch**: `015-zalopay-payment-integration`  
**Created**: 2026-11-22  
**Status**: Draft  
**Input**: User description: "Chuyển sang thanh toán bằng MoMo/ZaloPay sandbox. Implement ZaloPay Gateway v2: create → redirect → callback/query → cộng ví idempotent, không cần đăng ký tài khoản."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Tạo đơn nạp ví ZaloPay (Priority: P1)

As a logged-in user, I can request a ZaloPay payment for a VND amount so that I can pay on the ZaloPay sandbox checkout and have my wallet credited.

**Why this priority**: Entry point of the payment flow; without `order_url` no money enters the wallet.

**Independent Test**: `POST /api/v1/payment/zalopay/create` with `amount=1000`; response contains `paymentUrl` (ZaloPay `order_url`) and `appTransId`; a `PENDING` transaction is persisted.

**Acceptance Scenarios**:
1. Given authenticated user and amount > 0, when calling create, then backend signs with `key1`, calls `https://sb-openapi.zalopay.vn/v2/create`, stores `PENDING` transaction and returns `paymentUrl` + `appTransId`.
2. Given amount <= 0, when calling create, then 400 and no transaction is created.
3. Given unauthenticated request, then 401.
4. Given ZaloPay rejects the order, then transaction is marked `FAILED` and 400 is returned with gateway message.

### User Story 2 - Callback xác nhận và cộng ví idempotent (Priority: P1)

As the system, ZaloPay calls the callback after payment; backend verifies MAC, validates order/amount, credits wallet once, and responds in the format ZaloPay expects.

**Why this priority**: Callback is the authoritative notification; duplicate callbacks must never double-credit.

**Independent Test**: Replay the same valid callback twice; wallet balance increases by the amount exactly once.

**Acceptance Scenarios**:
1. Given valid `data` + MAC (HMAC-SHA256 with `key2`) for a `PENDING` transaction, then status becomes `SUCCESS`, wallet credited with type `ZALOPAY_TOPUP`, idempotency key = `appTransId`, response `{"return_code":1,"return_message":"success"}`.
2. Given the same callback replayed, then no extra credit and still `return_code=1`.
3. Given bad MAC / unknown `appTransId` / amount mismatch, then `return_code=-1` and no credit.
4. Given unexpected exception, then `return_code=0` so ZaloPay retries.

### User Story 3 - Return redirect từ cổng về frontend (Priority: P2)

As a user returning from ZaloPay checkout, backend verifies the redirect checksum, optionally queries gateway state, and redirects the browser to the frontend result page.

**Why this priority**: Completes the browser loop and handles lost callback.

**Independent Test**: Call return with valid checksum and `status=1` before callback arrives; backend queries ZaloPay, credits wallet (if confirmed), and 302-redirects to `${app.frontend-url}/payment/zalopay/result?status=success&appTransId=...`.

**Acceptance Scenarios**:
1. Given valid checksum + `status=1` + gateway `return_code=1`, then credit once and redirect `status=success`.
2. Given gateway `return_code=3` (pending), then redirect `status=pending`, no credit.
3. Given invalid checksum, then redirect `status=invalid-signature`, no state change.
4. Given transaction already `SUCCESS`, then redirect `status=success`, no double credit.

## Edge Cases
- Callback arrives before browser return → return sees `SUCCESS` and does not re-credit.
- `app_trans_id` must start with `yyMMdd` in GMT+7; implementation uses `Asia/Ho_Chi_Minh`.
- Duplicate create requests intentionally create different `appTransId` (no create idempotency).
- Sandbox uses public trial credentials (AppID 554) as defaults; production must override via env.

## Requirements *(mandatory)*

### Functional Requirements
- **FR-001**: Expose `POST /api/v1/payment/zalopay/create` (`@PreAuthorize("isAuthenticated()")`) accepting `amount`, optional `orderInfo`; return `paymentUrl`, `appTransId`.
- **FR-002**: Generate unique `appTransId` = `yyMMdd_<12 hex>` (GMT+7) and persist `ZalopayTransaction(PENDING)`.
- **FR-003**: Compute create MAC with `HMAC-SHA256(key1, app_id|app_trans_id|app_user|amount|app_time|embed_data|item)` and POST JSON to configurable create URL.
- **FR-004**: Send `callback_url` and `embed_data.redirecturl` from config (`app.zalopay.*`).
- **FR-005**: Expose public `POST /api/v1/payment/zalopay/callback` with JSON `{data, mac, type}`; verify `HMAC-SHA256(key2, data)` with constant-time compare.
- **FR-006**: Expose public `GET /api/v1/payment/zalopay/return` verifying redirect checksum with `key2`; 302 to frontend result URL.
- **FR-007**: All credits go through `WalletService.creditAvailable(..., idempotencyKey = appTransId)` guarded by transaction status + pessimistic lock.
- **FR-008**: Add `ZALOPAY_TOPUP` to `WalletTxType`.
- **FR-009**: No new SDK; use `javax.crypto.Mac` and `RestTemplateBuilder`.
- **FR-010**: Existing VNPay code, config and spec docs were removed; ZaloPay is the only gateway integration.

### Key Entities
- **ZalopayTransaction**: id, userId, appTransId (unique), amount, orderInfo, status (PENDING/SUCCESS/FAILED), zpTransId, channel, createdAt, updatedAt.
- **WalletTransaction**: existing; credited via `ZALOPAY_TOPUP`, `idempotencyKey = appTransId`.

### Non-Functional
- HMAC-SHA256 hex lowercase; UTF-8.
- HTTP timeouts: connect 5s, read 15s.
- Money stored as integer VND.

## Success Criteria *(mandatory)*
- **SC-001**: Sandbox create returns `return_code=1` and an `order_url` (verified manually 2026-11-22 with AppID 554).
- **SC-002**: Replaying callback twice changes wallet balance once.
- **SC-003**: Bad MAC returns `return_code=-1` with no credit.
- **SC-004**: `mvn -o compile` succeeds on JDK 17.

## Assumptions
- ZaloPay sandbox trial credentials are public; production overrides via `ZALOPAY_APP_ID`, `ZALOPAY_KEY1`, `ZALOPAY_KEY2`.
- Callback URL must be publicly reachable for real sandbox notifications (use ngrok/tunnel in local demo).
- Frontend result route: `${app.frontend-url}/payment/zalopay/result`.
