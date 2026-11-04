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

Push vào `dev` → GitHub Actions chạy `Backend tests`. Khi test xanh, job
`Bump superrepo submodule` cập nhật con trỏ submodule `backend` trong repo
`AI-Interview-Platform-deploy-staging` sang đúng SHA vừa test. Chính superrepo
đó mới gọi Render deploy hook để build + deploy — service Render đang đặt
`autoDeployTrigger: off` nên không tự chạy theo commit nữa.

Cần secret **`DEPLOY_PAT`**: fine-grained token, chỉ cấp repo
`AI-Interview-Platform-deploy-staging`, quyền **Contents: read and write**
(bot push commit bump submodule).

Lưu ý: Render build từ đầu nhánh `dev` của repo này, không build đúng SHA mà
superrepo đang trỏ. Muốn deploy lại chính xác một SHA thì tạo branch/tag từ
SHA đó rồi trỏ service Render sang branch ấy.

Tài liệu đặc tả và quy ước ở trong repo này: `spec/` (feature notes, known gaps) và
`specs/NNN-*/` (đặc tả từng feature theo Spec Kit).

## AI service (avatar / STT / TTS / LLM) nằm ở repo riêng

Toàn bộ Python phục vụ luồng phỏng vấn avatar — LiveTalking, FastAPI orchestration, script bring-up
Colab — đã tách sang <https://github.com/PNV-do-an/AI-Interview-Platform-AI>. Repo này chỉ giữ:

- hợp đồng HTTP mà FE/BE đã thống nhất: `specs/012-avatar-interview-api/spec.md`;
- phần transcript relay + rubric self-assessment phía Spring Boot.

Vì vậy repo này không còn `Dockerfile` Python, `docker-compose.yml`, `app/`, `scripts/*.py` hay
`config/livetalking*.yaml`. Image backend build từ `Dockerfile.backend` (đường dẫn khai trong
`render.yaml`).
