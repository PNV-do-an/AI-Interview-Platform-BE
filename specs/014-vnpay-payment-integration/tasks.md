# Tasks: VNPay Payment Gateway Integration

**Input**: Design documents from `/specs/014-vnpay-payment-integration/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/vnpay-payment-api.md

**Tests**: Not requested in spec (optional); manual verification via quickstart.md.

**Organization**: Tasks grouped by user story. All three user stories are P1 but US2/US3 build on US1's service.

## Phase 1: Setup (Shared Infrastructure)

- [X] T001 Add VNPay config block to `src/main/resources/application.yml` (app.vnpay.* with env overrides)
- [X] T002 Add `VNPAY_TOPUP` value to `src/main/java/com/aiinterview/platform/model/enums/WalletTxType.java`

## Phase 2: Foundational (Blocking Prerequisites)

- [X] T003 [P] Create enum `VnpayStatus` (PENDING, SUCCESS, FAILED) in `src/main/java/com/aiinterview/platform/model/enums/VnpayStatus.java`
- [X] T004 [P] Create entity `VnpayTransaction` in `src/main/java/com/aiinterview/platform/model/entity/VnpayTransaction.java` (fields per data-model.md; unique txnRef; PrePersist timestamps)
- [X] T005 Create repository `VnpayTransactionRepository` in `src/main/java/com/aiinterview/platform/model/repository/VnpayTransactionRepository.java` (findByTxnRef, findByTxnRefForUpdate with PESSIMISTIC_WRITE lock, findByUserIdOrderByCreatedAtDesc)
- [X] T006 Create service interface `VnpayService` in `src/main/java/com/aiinterview/platform/service/payment/VnpayService.java`
- [X] T007 Implement `VnpayServiceImpl` in `src/main/java/com/aiinterview/platform/service/payment/impl/VnpayServiceImpl.java` (config injection, URL build, HMAC-SHA512 sign/verify, createTransaction, processReturn, processIpn, idempotent credit via WalletService)

**Checkpoint**: Foundation ready — user story implementation can begin.

## Phase 3: User Story 1 - Create VNPay payment URL (Priority: P1) 🎯 MVP

**Goal**: Authenticated user receives a signed VNPay payment URL + txnRef and a PENDING record is stored.

**Independent Test**: POST /api/v1/payment/vnpay/create returns paymentUrl + txnRef; URL opens VNPay checkout.

- [X] T008 [US1] Create DTO `VnpayCreateRequest` in `src/main/java/com/aiinterview/platform/model/dto/request/VnpayCreateRequest.java` (amount @Min(1), optional orderInfo)
- [X] T009 [US1] Create DTO `VnpayCreateResponse` in `src/main/java/com/aiinterview/platform/model/dto/response/VnpayCreateResponse.java` (paymentUrl, txnRef)
- [X] T010 [US1] Implement create flow in `VnpayServiceImpl` (validate amount, generate txnRef, save PENDING, build signed URL)
- [X] T011 [US1] Create controller `VnpayController` in `src/main/java/com/aiinterview/platform/controller/payment/VnpayController.java` with POST /create (@PreAuthorize isAuthenticated)

**Checkpoint**: US1 independently testable.

## Phase 4: User Story 2 - Handle VNPay return URL and credit wallet (Priority: P1)

**Goal**: Verify signature on browser return, update status, credit wallet once (idempotent), redirect to frontend result page.

**Independent Test**: Replay return; wallet credited once; invalid signature redirects invalid-signature.

- [X] T012 [US2] Implement processReturn in `VnpayServiceImpl` (verify hash, lookup txnRef, amount check, status update, idempotent credit, redirect URL builder)
- [X] T013 [US2] Add GET /return handler in `VnpayController` (public, no @PreAuthorize) returning 302 redirect

**Checkpoint**: US2 independently testable.

## Phase 5: User Story 3 - IPN server-to-server confirmation (Priority: P1)

**Goal**: VNPay calls IPN; backend verifies, updates idempotently, credits once, returns VNPay JSON.

**Independent Test**: IPN valid → RspCode 00; replay → 00 no double credit; invalid sig → 97; unknown → 01; amount mismatch → 04.

- [X] T014 [US3] Implement processIpn in `VnpayServiceImpl` returning RspCode/Message (map outcomes per research R6)
- [X] T015 [US3] Add GET (and POST) /ipn handler in `VnpayController` (public) returning Map {RspCode, Message}

**Checkpoint**: All user stories independently functional.

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T016 [P] Run quickstart.md verification steps against sandbox (requires VNPay credentials; manual)
- [X] T017 Code cleanup / logging review in VnpayServiceImpl

## Dependencies & Execution Order

- Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6
- US2/US3 depend on US1's service (shared VnpayServiceImpl).
- Foundational (entity/repo/service skeleton) blocks all user stories.

## Parallel Opportunities

- T003 and T004 can run in parallel (different files).
- T008 and T009 can run in parallel.

## Implementation Strategy

### MVP First (US1)
Complete Phases 1–3, verify create flow end-to-end before US2/US3.

### Incremental Delivery
Add US2 (return) then US3 (IPN), validating idempotency at each step.