# Feature Specification: payOS Payment Integration

**Feature Branch**: `016-payos-payment-integration`  
**Created**: 2026-11-24  
**Status**: Draft  
**Input**: User description: "Bỏ thanh toán bằng ZaloPay và MoMo, dùng payOS cho nạp ví."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Tạo link thanh toán payOS (Priority: P1)

As a logged-in user, I can request a payOS payment link for a VND amount so that I can pay via bank transfer and have my wallet credited.

**Independent Test**: `POST /api/v1/payment/payos/create` with `amount=1000` returns `checkoutUrl`, `orderCode`; a `PENDING` `PayosTransaction` is stored.

**Acceptance Scenarios**:
1. Authenticated + amount > 0 → backend signs request with `checksumKey`, calls `POST {baseUrl}/v2/payment-requests`, stores PENDING, returns `checkoutUrl`.
2. amount <= 0 → 400, no transaction.
3. Unauthenticated → 401.
4. payOS rejects (`code != 00`) → transaction FAILED, 400 with gateway message.

### User Story 2 - Webhook xác nhận + cộng ví idempotent (Priority: P1)

As the system, payOS calls webhook after payment; backend verifies HMAC-SHA256 signature over sorted `data`, validates order/amount, credits wallet once.

**Independent Test**: Replay the same webhook twice; wallet balance increases once.

**Acceptance Scenarios**:
1. Valid signature + `data.code=00` + matching order/amount → SUCCESS, `creditAvailable(..., idempotencyKey = orderCode)`, response `{"code":"00","desc":"success","success":true}`.
2. Replay → no extra credit.
3. Bad signature / unknown order / amount mismatch → `code=01`, no credit.
4. Failed payment webhook → transaction FAILED, no credit, still 2XX ack.

### User Story 3 - Return/Cancel redirect (Priority: P2)

As a user returning from checkout, backend queries payOS `GET /v2/payment-requests/{orderCode}` (signature verified) and 302-redirects to frontend result page.

**Acceptance Scenarios**:
1. `status=PAID` + query confirmed → credit once, redirect `status=success`.
2. `status=PENDING|PROCESSING` → redirect `status=pending`, no credit.
3. `cancel=true` or `status=CANCELLED` → mark FAILED, redirect `status=failed`.

## Requirements *(mandatory)*

- **FR-001**: Expose `POST /api/v1/payment/payos/create` with `@PreAuthorize("isAuthenticated()")`.
- **FR-002**: Generate unique integer `orderCode` (millis × 1000 + random 3 digits) and persist `PayosTransaction(PENDING)`.
- **FR-003**: Sign create payload as `amount=&cancelUrl=&description=&orderCode=&returnUrl=` sorted alphabetically with `HMAC-SHA256(checksumKey)`.
- **FR-004**: Send `x-client-id` and `x-api-key` headers to payOS.
- **FR-005**: Expose public `POST /api/v1/payment/payos/webhook`; verify signature over sorted `data` fields (null → `""`).
- **FR-006**: Expose public `GET /api/v1/payment/payos/return` and `/cancel`, 302 to `${app.frontend-url}/payment/payos/result`.
- **FR-007**: Credits via `WalletService.creditAvailable(..., "PAYOS_TOPUP", ..., idempotencyKey = orderCode)` guarded by pessimistic lock + status.
- **FR-008**: Add `PAYOS_TOPUP` to `WalletTxType`; remove ZaloPay/MoMo code, config, docs and enum values.
- **FR-009**: No new SDK; use `javax.crypto.Mac` + `RestTemplateBuilder`.
- **FR-010**: Config via `app.payos.*` with env overrides `PAYOS_CLIENT_ID`, `PAYOS_API_KEY`, `PAYOS_CHECKSUM_KEY`, `PAYOS_BASE_URL`, `PAYOS_RETURN_URL`, `PAYOS_CANCEL_URL`.

## Key Entities
- **PayosTransaction**: id, userId, orderCode (unique), amount, description (≤9 chars), status, paymentLinkId, reference, timestamps.
- **WalletTransaction**: existing; type `PAYOS_TOPUP`, `idempotencyKey = orderCode`.

## Success Criteria
- `mvn -o test-compile` succeeds on JDK 17.
- `OrderServiceImplTest` passes (14 tests).
- Replay of webhook does not double-credit.
- Bad signature returns `code=01` with no credit.

## Assumptions
- payOS **has no separate sandbox**; integration is tested with a real personal account and small amounts (official docs: https://payos.vn/docs/moi-truong-test).
- Credentials must be supplied by user; no public trial key exists.
- Webhook URL must be publicly reachable; register it in payOS dashboard via `POST /confirm-webhook`.
