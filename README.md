# AI Interview Platform — Backend

Spring Boot 3.2 / Java 17, REST API phục vụ màn phỏng vấn AI.

## Chạy local

```bash
cp .env.example .env        # sửa DB_PASSWORD, MAIL_* theo máy
mvn spring-boot:run         # cổng 8080
```

Cần MySQL đang chạy ở `DB_HOST:DB_PORT` (mặc định `localhost:3307`).

## Test

```bash
mvn -B clean test
```

Bắt buộc có `clean`: `target/classes` còn `.class` của các bean cũ sẽ làm
`PersistenceTest` thấy 2 bean trùng `LoginAttemptRepository`. Hai test
Testcontainers chỉ chạy được ở môi trường có Docker (CI), trên Windows
`mvn test` báo xanh giả vì không tìm thấy Docker.

## CI/CD

Push vào `dev` → GitHub Actions chạy `Backend tests` → Render chỉ deploy khi
check đó xanh (`autoDeployTrigger: checksPass` trong `render.yaml`). Sau đó job
`Report to deploy repo` báo SHA sang repo
`AI-Interview-Platform-deploy-staging` để ghi status lên bảng theo dõi chung —
cần secret **`DEPLOY_PAT`** (fine-grained token, chỉ repo deploy, quyền
*Actions: read and write*).

Tài liệu đặc tả và quy ước (`spec/`) nằm ở repo
`AI-Interview-Platform-deploy-staging`.
