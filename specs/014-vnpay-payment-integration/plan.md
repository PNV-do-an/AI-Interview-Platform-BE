# Implementation Plan: VNPay Payment Gateway Integration

**Branch**: `014-vnpay-payment-integration` | **Date**: 2026-11-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/014-vnpay-payment-integration/spec.md`

## Summary

Implement VNPay payment gateway integration for wallet top-up: an authenticated create endpoint that builds a signed VNPay payment URL, a public return endpoint that verifies the signature and credits the wallet once, and a public IPN endpoint for server-to-server confirmation. Reuse existing `WalletService` credit path with idempotency key = txnRef. Add a `VnpayTransaction` entity, a `VnpayService`, a `VnpayController`, VNPay config, and an enum value `VNPAY_TOPUP`.

## Technical Context

**Language/Version**: Java 17 (Spring Boot 3.2.5)
**Primary Dependencies**: Spring Boot Web, Spring Security, Spring Data JPA, MySQL, Lombok, Jackson. No new external libs (HMAC via `javax.crypto.Mac`).
**Storage**: MySQL (JPA `ddl-auto=update`); new table `vnpay_transactions`.
**Testing**: JUnit 5 + Testcontainers (CI only); manual curl/Postman against sandbox.
**Target Platform**: Linux server (Render) / local Windows dev.
**Project Type**: Web service (backend only).
**Performance Goals**: Create < 200ms; return/IPN < 500ms including DB write.
**Constraints**: Idempotent credit; signature verification mandatory; public (unauthenticated) return & IPN; money as integer VND.
**Scale/Scope**: Top-up flow only; one new controller, one service, one entity, one repository, config additions.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Hợp đồng API trước, code sau**: Contract documented in `contracts/vnpay-payment-api.md` before code. ✓
- **II. Số đo thật**: Quickstart lists curl commands with expected responses; verification by real sandbox run. ✓
- **III. Chặn lúc ghi**: Amount validation at create; signature/amount checks before credit. ✓
- **IV. Audit thật**: N/A (no admin action). ✓
- **V. Hợp đồng lỗi nhất quán**: `ApiResponse` for success, `ErrorModel` for errors via GlobalExceptionHandler. IPN uses VNPay-required JSON (exception to envelope by gateway contract). ✓
- **VI. Phòng vệ nhiều tầng**: Create uses `@PreAuthorize(isAuthenticated())`; return/IPN intentionally public (registered in security as permitAll, but `anyRequest().permitAll()` already). ✓
- **VII. Phạm vi tối thiểu**: Only top-up flow; no UI, no escrow changes, no refund flow. ✓
- **VIII. Kiểm chứng trước workaround**: N/A.

**Gate result**: PASS.

## Project Structure

### Documentation (this feature)

```text
specs/014-vnpay-payment-integration/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── vnpay-payment-api.md
└── checklists/
    └── requirements.md
```

### Source Code (repository root)

```text
src/main/java/com/aiinterview/platform/
├── config/
│   └── VnpayConfig.java            # @ConfigurationProperties app.vnpay (or read via @Value in service)
├── controller/payment/
│   └── VnpayController.java
├── model/
│   ├── entity/VnpayTransaction.java
│   ├── enums/VnpayStatus.java       # PENDING/SUCCESS/FAILED
│   └── repository/VnpayTransactionRepository.java
├── service/payment/
│   ├── VnpayService.java
│   └── impl/VnpayServiceImpl.java
└── common/util/
    └── (reuse none; HMAC helper inside service)
src/main/resources/
└── application.yml                 # app.vnpay.* additions
```

**Structure Decision**: Follow existing package conventions (`controller/payment`, `service/payment/impl`, `model/entity`, `model/repository`, `model/enums`). No new module.

## Complexity Tracking

No constitution violations. (Empty.)