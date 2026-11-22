# Feature Specification: VNPay Payment Gateway Integration

**Feature Branch**: `014-vnpay-payment-integration`

**Created**: 2026-11-22

**Status**: Draft

**Input**: User description: "Tạo URL thanh toán VNPay (/api/v1/payment/vnpay/create) — sinh tham số vnp_* + chữ ký HMAC SHA512, redirect user sang sandbox.vnpayment.vn (hoặc production). Xử lý return URL (/api/v1/payment/vnpay/return) — VNPay redirect về với vnp_ResponseCode, vnp_TxnRef, vnp_SecureHash; backend verify chữ ký và credit ví. IPN (Instant Payment Notification) — VNPay gọi server-to-server để xác nhận giao dịch (bắt buộc cho production)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Create VNPay payment URL to top up wallet (Priority: P1)

As a logged-in user, I can request a VNPay payment URL for a chosen VND amount so that I can pay via VNPay and have my wallet credited after successful payment.

**Why this priority**: This is the entry point of the whole payment flow; without it no money enters the wallet through VNPay.

**Independent Test**: Call POST /api/v1/payment/vnpay/create with a valid amount; response contains a paymentUrl pointing to VNPay sandbox and a unique txnRef; opening the URL shows VNPay checkout.

**Acceptance Scenarios**:

1. Given an authenticated user with amount 100000 VND, When calling create, Then a paymentUrl is returned, a PENDING transaction record is stored with txnRef, and the URL contains vnp_SecureHash signed with HMAC SHA512.
2. Given amount <= 0, When calling create, Then 400 BadRequest and no transaction is created.
3. Given an unauthenticated request, When calling create, Then 401.

### User Story 2 - Handle VNPay return URL and credit wallet (Priority: P1)

As a user returning from VNPay, the backend verifies the secure hash and, on success, credits the wallet once (idempotently), then redirects the browser to the frontend result page.

**Why this priority**: Completes the user-facing payment loop and prevents duplicate credit on refresh.

**Independent Test**: Simulate VNPay return with valid signature and ResponseCode=00; wallet balance increases by amount once; a second identical return does not change balance.

**Acceptance Scenarios**:

1. Given a valid return with vnp_ResponseCode=00 and matching hash, When processed, Then the transaction becomes SUCCESS, wallet is credited by vnp_Amount/100, and browser is redirected to frontend result page with status=success.
2. Given an invalid/missing hash, When processed, Then 400/redirect with status=invalid-signature and no credit.
3. Given vnp_ResponseCode != 00, When processed, Then transaction becomes FAILED, no credit, redirect with status=failed.
4. Given the same return is replayed, Then no duplicate credit (idempotent).

### User Story 3 - IPN server-to-server confirmation (Priority: P1)

As the system, VNPay calls the IPN endpoint to confirm the transaction; backend verifies signature, updates status idempotently, credits wallet if not already credited, and responds with the VNPay-required JSON.

**Why this priority**: Mandatory for production reliability; handles cases where the browser return is lost.

**Independent Test**: POST/GET IPN with valid signature and ResponseCode=00; response body is {"RspCode":"00","Message":"Confirm Success"}; wallet credited once.

**Acceptance Scenarios**:

1. Given a valid IPN with ResponseCode=00 for a PENDING transaction, When processed, Then transaction SUCCESS, wallet credited, response RspCode=00.
2. Given IPN for an already SUCCESS transaction, When processed, Then no double credit, response RspCode=00 with message "Order already confirmed" (or 00/Confirm Success).
3. Given invalid signature, When processed, Then response RspCode=97.
4. Given unknown txnRef, When processed, Then response RspCode=01.
5. Given amount mismatch, When processed, Then response RspCode=04.

### Edge Cases

- What happens when vnp_Amount does not match stored amount? IPN returns RspCode=04 and no credit.
- How does system handle expired payment (past vnp_ExpireDate)? Create new txnRef on retry; old PENDING may be marked expired lazily or left; no credit.
- What happens when IPN arrives before browser return? Wallet credited by IPN; return sees already SUCCESS and does not double credit.
- What happens on hash mismatch in return? Redirect to frontend with status=invalid-signature, no state change.
- Duplicate create requests: each creates a new txnRef (no idempotency on create); acceptable.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST expose POST /api/v1/payment/vnpay/create (authenticated) accepting amount (VND, > 0) and optional orderInfo, returning paymentUrl and txnRef.
- **FR-002**: System MUST generate a unique txnRef per create (e.g. timestamp + random or UUID) and persist a PENDING VnpayTransaction.
- **FR-003**: System MUST build the VNPay payment URL with all required vnp_* parameters and a vnp_SecureHash computed as HMAC SHA512 over the sorted parameter string, using the configured hash secret.
- **FR-004**: System MUST support sandbox and production VNPay endpoints via configuration (app.vnpay.pay-url, tmn-code, hash-secret, return-url, ipn-url).
- **FR-005**: System MUST expose GET /api/v1/payment/vnpay/return that verifies vnp_SecureHash, updates transaction status, credits wallet on success (idempotently), and redirects browser to frontend result page.
- **FR-006**: System MUST expose GET (or POST) /api/v1/payment/vnpay/ipn that verifies signature, validates txnRef/amount, updates status idempotently, credits wallet once, and returns VNPay-required JSON {"RspCode":..., "Message":...}.
- **FR-007**: All wallet credits from VNPay MUST use WalletService.creditAvailable (or topUp) with idempotency key = txnRef so duplicate processing never double-credits.
- **FR-008**: System MUST reject (no credit) when signature invalid, txnRef unknown, or amount mismatch.
- **FR-009**: System MUST store money as integer VND; vnp_Amount is amount*100 and converted back on verification.
- **FR-010**: Create/return/IPN logic MUST be transactional where wallet mutation occurs.

### Key Entities

- **VnpayTransaction**: id, userId, txnRef (unique), amount (VND), orderInfo, status (PENDING/SUCCESS/FAILED), vnpTransactionNo, bankCode, payDate, responseCode, createdAt, updatedAt.
- **Wallet / WalletTransaction**: existing; credited via VNPAY_TOPUP type (add enum value) with idempotencyKey=txnRef.

### Non-Functional

- Hash algorithm: HMAC SHA512.
- Parameter encoding: UTF-8; sort keys alphabetically; join as key=value with "&".
- Timezone for vnp_CreateDate/vnp_ExpireDate: GMT+7 (Asia/Ho_Chi_Minh) per VNPay spec.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A create call returns a paymentUrl that VNPay accepts (HTTP 200 on GET) and shows checkout.
- **SC-002**: Replay of the same return/IPN does not change wallet balance (idempotent).
- **SC-003**: Invalid signature returns RspCode=97 (IPN) / redirect invalid-signature (return) and no credit.
- **SC-004**: Unknown txnRef returns RspCode=01 (IPN).
- **SC-005**: Amount mismatch returns RspCode=04 (IPN).

## Assumptions

- VNPay sandbox credentials provided via env (VNPAY_TMN_CODE, VNPAY_HASH_SECRET); production uses same code with different env values and pay-url.
- Wallet credit happens on successful payment (ResponseCode=00); no escrow involved for top-up.
- Frontend result page route is `${app.frontend-url}/payment/vnpay/result` (configurable).
- Return URL and IPN URL are publicly reachable (no auth) and registered with VNPay merchant portal.
- User identity for credit is resolved from VnpayTransaction.userId (not from VNPay params).

Now let me persist feature.json (already created by script). Let me verify.<|end|><|start|> assistant to=shell<|channel|>analysis <|constrain|>xml<|message|><rfl:parameter name="command">
cat D:\AI-Interview-Platform-BE\.specify\feature.json