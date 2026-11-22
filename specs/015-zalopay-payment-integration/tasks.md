# Tasks: ZaloPay Payment Gateway Integration

## Phase 1: Setup & Model
- [x] T001 Add `ZALOPAY_TOPUP` to `WalletTxType`
- [x] T002 Add `app.zalopay.*` config block to `application.yml` with env overrides
- [x] T003 Create `ZalopayStatus`, `ZalopayTransaction`, `ZalopayTransactionRepository`

## Phase 2: DTOs & Service
- [x] T004 Create `ZalopayCreateRequest`, `ZalopayCallbackRequest`, `ZalopayCreateResponse`
- [x] T005 Create `ZalopayService` interface
- [x] T006 Implement `ZalopayServiceImpl`: create/query HMAC with `key1`, callback/redirect verify with `key2`
- [x] T007 Idempotent credit via `WalletService.creditAvailable(..., appTransId)` + pessimistic lock + status guard

## Phase 3: API
- [x] T008 Create `ZalopayController` with `create`, `callback`, `return`

## Phase 4: Verification
- [x] T009 `JAVA_HOME=...ms-17.0.20.1 mvn -o compile` → BUILD SUCCESS
- [x] T010 Manual sandbox create call → `return_code=1`, `order_url` returned (AppID 554)
- [ ] T011 End-to-end callback with public tunnel (ngrok) + balance assertion (manual, needs frontend/tunnel)
