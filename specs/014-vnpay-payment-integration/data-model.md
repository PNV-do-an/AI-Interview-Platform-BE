# Data Model: VNPay Payment Gateway Integration

## New Entities

### VnpayTransaction

Table: `vnpay_transactions`

| Field | Type | Constraints | Notes |
|---|---|---|---|
| id | BIGINT | PK, auto | |
| user_id | BIGINT | NOT NULL | Owner (top-up target) |
| txn_ref | VARCHAR(64) | NOT NULL, UNIQUE | Our order reference sent to VNPay as vnp_TxnRef |
| amount | BIGINT | NOT NULL | VND (integer) |
| order_info | VARCHAR(255) | | vnp_OrderInfo |
| status | VARCHAR(20) | NOT NULL | PENDING / SUCCESS / FAILED |
| vnp_transaction_no | VARCHAR(64) | | VNPay's vnp_TransactionNo |
| bank_code | VARCHAR(32) | | vnp_BankCode |
| pay_date | VARCHAR(32) | | vnp_PayDate (yyyyMMddHHmmss) |
| response_code | VARCHAR(8) | | vnp_ResponseCode |
| created_at | DATETIME | NOT NULL | |
| updated_at | DATETIME | NOT NULL | |

Indexes: unique `txn_ref`; index `user_id`.

### Enum: VnpayStatus

`PENDING`, `SUCCESS`, `FAILED`.

## Modified Entities

### WalletTxType (enum)

Add value `VNPAY_TOPUP`.

### WalletTransaction (existing)

No schema change. New rows use `type=VNPAY_TOPUP`, `referenceType=VNPAY`, `idempotencyKey=txnRef`, `amount=+amount`, `balanceAfter`, `description`.

## State Transitions

```
PENDING --(ResponseCode=00 & valid sig & amount match)--> SUCCESS (credit wallet)
PENDING --(ResponseCode!=00)--> FAILED
PENDING --(invalid sig / unknown / amount mismatch)--> no state change (FAILED only on explicit non-00)
SUCCESS --(replay)--> SUCCESS (no-op, idempotent)
```

## Repository

`VnpayTransactionRepository extends JpaRepository<VnpayTransaction, Long>`:
- `Optional<VnpayTransaction> findByTxnRef(String txnRef)`
- `Optional<VnpayTransaction> findByTxnRefForUpdate(String txnRef)` (with `@Lock(PESSIMISTIC_WRITE)`)
- `List<VnpayTransaction> findByUserIdOrderByCreatedAtDesc(Long userId)` (optional, for history)

## Validation Rules

- amount > 0 at create (FR-001).
- At IPN/return: signature must match; txnRef must exist; amount (vnp_Amount/100) must equal stored amount.