# Tasks: payOS Payment Integration

## Phase 1: Remove legacy gateways
- [x] T001 Remove legacy gateway controllers/services/DTOs/entities/repositories/enums/specs
- [x] T002 Remove legacy gateway config blocks
- [x] T003 Set `PAYOS_TOPUP` as the only gateway wallet type in `WalletTxType`
- [x] T004 Update order test payment method label to `PAYOS`

## Phase 2: payOS model & config
- [x] T005 Add `app.payos.*` config block with env overrides
- [x] T006 Create `PayosStatus`, `PayosTransaction`, `PayosTransactionRepository`
- [x] T007 Create `PayosCreateRequest`, `PayosWebhookRequest`, `PayosCreateResponse`

## Phase 3: Service & API
- [x] T008 Create `PayosService` interface
- [x] T009 Implement `PayosServiceImpl`: create signature, webhook signature, query-order signature
- [x] T010 Idempotent credit via `WalletService.creditAvailable(..., orderCode)` + pessimistic lock
- [x] T011 Create `PayosController` with `create`, `webhook`, `return`, `cancel`

## Phase 4: Verification
- [x] T012 `mvn -o test-compile` → BUILD SUCCESS
- [x] T013 `mvn -o -Dtest=OrderServiceImplTest test` → 14/14 pass
- [x] T014 End-to-end with real payOS credentials: create link OK, query-response signature matches payOS, replayed webhook credits once, bad signature rejected
