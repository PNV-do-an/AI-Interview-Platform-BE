# Quickstart: ZaloPay sandbox top-up

## Config (defaults already in `application.yml`)

```yaml
app:
  zalopay:
    app-id: ${ZALOPAY_APP_ID:554}
    key1: ${ZALOPAY_KEY1:8NdU5pG5R2spGHGhyO99HN1OhD8IQJBn}
    key2: ${ZALOPAY_KEY2:uUfsWgfLkRLzq6W2uNXTCxrfxs51auny}
    create-url: ${ZALOPAY_CREATE_URL:https://sb-openapi.zalopay.vn/v2/create}
    query-url: ${ZALOPAY_QUERY_URL:https://sb-openapi.zalopay.vn/v2/query}
    callback-url: ${ZALOPAY_CALLBACK_URL:http://localhost:8080/api/v1/payment/zalopay/callback}
    redirect-url: ${ZALOPAY_REDIRECT_URL:http://localhost:3000/payment/zalopay/result}
```

For real callback notifications, expose localhost with a tunnel and set `ZALOPAY_CALLBACK_URL` to the public URL.

## 1. Tạo đơn

```bash
curl -X POST http://localhost:8080/api/v1/payment/zalopay/create \
  -H "Authorization: Bearer <JWT>" -H "Content-Type: application/json" \
  -d '{"amount":1000,"orderInfo":"Nap tien vi"}'
```

Response:

```json
{"success":true,"data":{"paymentUrl":"https://qcgateway.zalopay.vn/openinapp?order=...","appTransId":"261122_xxxxxxxxxxxx"}}
```

Mở `paymentUrl` trên trình duyệt, chọn phương thức thanh toán sandbox và xác nhận.

## 2. Callback (ZaloPay → backend)

`POST /api/v1/payment/zalopay/callback` với `{"data":"<json>","mac":"<hmac key2>","type":1}`. Backend trả `{"return_code":1,"return_message":"success"}`.

## 3. Return (ZaloPay → browser)

`GET /api/v1/payment/zalopay/return?appid=...&apptransid=...&status=1&...&checksum=...` → 302 về frontend `?status=success|pending|failed|invalid-signature&appTransId=...`.

## 4. Kiểm tra ví

```bash
curl http://localhost:8080/api/v1/wallet -H "Authorization: Bearer <JWT>"
```

Gọi callback/return lặp lại → số dư không đổi (idempotent).
