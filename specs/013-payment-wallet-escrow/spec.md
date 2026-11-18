# Feature Specification: Payment, Wallet, and Escrow

**Feature Branch**: `feature/create-pay`

**Created**: 2026-11-18

**Status**: Draft

**Input**: Backend payment function per "Đánh giá logic thanh toán – AI Interview Platform" plan.

## User Scenarios & Testing

### User Story 1 - Wallet, Top-up and Ledger (Priority: P1)

As a user, I can top up an internal wallet and see a consistent balance so that I can pay for mentors and AI interviews.

**Independent Test**: Top up via idempotent webhook twice with same transaction id; balance increases once; ledger has one entry.

**Acceptance Scenarios**:
1. Given a user with zero balance, When a top-up webhook with a new provider transaction id is received, Then wallet balance increases and a ledger entry is created.
2. Given the same webhook is received again, Then balance does not change and no duplicate ledger entry is created.
3. Given insufficient balance, When booking a mentor slot, Then request is rejected with 400 and balance unchanged.

### User Story 2 - Mentor Booking with Escrow (Priority: P1)

As a candidate, I can book an available mentor slot; funds are held in escrow and released to mentor (90%) after completion or 48h without complaint.

**Independent Test**: Book slot with enough balance; balance decreases; escrow record created; after completion, mentor available balance increases by 90%.

**Acceptance Scenarios**:
1. Given sufficient wallet balance, When booking a slot, Then slot is marked booked, funds move to escrow, booking status HELD.
2. Given a completed booking without complaint for 48h, When release runs, Then mentor receives 90% and platform keeps 10% commission (rate locked at booking).
3. Given cancellation >=24h before start, Then full refund to candidate.
4. Given cancellation <24h before start, Then partial refund (hold amount) to candidate and rest to mentor.
5. Given mentor no-show, Then full refund to candidate.

### User Story 3 - Withdrawals (Priority: P2)

As a mentor, I can request withdrawal of available balance; funds are deducted immediately and restored on rejection.

**Acceptance Scenarios**:
1. Given available balance >= min, When withdrawal request created, Then available balance decreases and status PENDING.
2. Given admin approves, Then status APPROVED/PAID.
3. Given admin rejects, Then amount restored to available balance.

### User Story 4 - Mentor Pricing Floor/Ceiling (Priority: P2)

Admin can set per-mentor price floors/ceilings by level; backend validates slot price.

### User Story 5 - AI Interview Credits & Difficulty (Priority: P3)

Questions have difficulty EASY/MEDIUM/HARD; users buy AI interview credits; free tier only EASY.

### User Story 6 - Prepaid Packages & Tiered Commission (Priority: P4)

Prepaid AI credit packages with max 5% discount (platform-funded); tiered commission 15% new / 10% repeat.

## Requirements

### Functional Requirements
- FR-001: Store money as integer VND in wallet and ledger.
- FR-002: Wallet balance computed from ledger entries; maintain available and pending balances.
- FR-003: Top-up processing must be idempotent by provider transaction id.
- FR-004: All balance mutations occur within DB transaction with row locking.
- FR-005: Booking must lock commission rate at booking time.
- FR-006: Escrow release must be idempotent.
- FR-007: Withdrawal must deduct immediately on creation.
- FR-008: Pricing floor/ceiling enforced in backend.
- FR-009: Question difficulty enforced; default mix 30/50/20.

### Key Entities
- Wallet, LedgerEntry, EscrowBooking, Withdrawal, MentorPricing, AiCreditPackage, AiCreditLedger, Question (difficulty).

## Success Criteria
- SC-001: Double webhook does not double credit.
- SC-002: Concurrent booking cannot overdraw wallet.
- SC-003: Commission rate locked per booking.

## Assumptions
- Payment gateway abstracted; default mock provider for dev.
- Admin approval for withdrawals in phase 1.
- No real tax handling.
