# AI-Interview-Platform-AI

Python AI service cho luồng phỏng vấn avatar (HireMind). Tách khỏi repo BE
theo spec 012 (`AI-Interview-Platform-BE/specs/012-avatar-interview-api/spec.md`):
BE Spring Boot chỉ giữ hợp đồng dữ liệu + authorization; mọi thứ avatar/STT/TTS/LLM ở đây.

## Kiến trúc (MVP P0–P3)

```
React FE ──WebRTC (âm thanh + video avatar)── LiveTalking (:8010)      ← avatar + máy miệng + EdgeTTS
   │ HTTPS                       ▲
   │                              │ /offer /human /is_speaking /interrupt_talk
   │                              ▼
   ├─ chữ hóa + câu hỏi ──── Python AI service (:8020)                 ← app/ (P1, đã dựng + đã đo)
   │ POST /api/v1/mock-interviews/{id}/answer|evaluation (transcript, JWT ứng viên)
   ▼
Spring Boot BE (spec 012) — chỉ hợp đồng dữ liệu + authorization + tính overallScore
```

- `vendored/LiveTalking/` — engine avatar Apache-2.0, model `wav2lip` (GPU 4GB vừa).
  Không sửa code trong này trừ khi có patch đánh dấu rõ; nâng cấp bằng cách pull lại.
- `app/` — AI service FastAPI: ký gửi SDP, sinh câu hỏi theo JD, chữ hóa bằng faster-whisper,
  điều phối phiên trong RAM, chấm rubric. Mô tả từng route + số đo: `docs/P1-ai-service.md`.
- `scripts/` — tiện ích verify môi trường, bootstrap weights, đo P0/P1.

## Cài đặt một lần (Windows, GTX 1650 4GB, driver 516.40 = CUDA 11.7 → phải dùng torch cu118)

```bash
# từ D:/AI-Interview-Platform-AI
python -m venv .venv
.venv/Scripts/python.exe -m pip install torch==2.4.1+cu118 torchvision==0.19.1+cu118 \
    torchaudio==2.4.1+cu118 --index-url https://download.pytorch.org/whl/cu118
.venv/Scripts/python.exe -m pip install -r vendored/LiveTalking/requirements.txt
```

> Lưu ý: cu121 yêu cầu driver ≥ 527; driver 516 chỉ chạy được cu118.
> Nếu nâng driver NVIDIA lên bản mới (≥527) thì đổi lại cu121/cu124.

## Trọng số (P0)

Tải từ mirror Apache-2.0 `shibing624/ai-avatar-wav2lip` (HF), đặt:

| File | Vị trí |
|---|---|
| `wav2lip.pth` (215MB) | `vendored/LiveTalking/models/wav2lip.pth` |
| avatar zip (female/glass_man/long_hair_girl) | giải nén vào `vendored/LiveTalking/data/avatars/<tên>/` |

Avatar folder phải có `coords.pkl`, `full_imgs/`, `face_imgs/` (đọc `avatars/wav2lip_avatar.py:load_avatar`).
Chạy `scripts/bootstrap_weights.py` để tự kiểm tra.

## Chạy P0

```bash
cd D:/AI-Interview-Platform-AI
.venv/Scripts/python.exe scripts/verify_env.py          # CUDA + VRAM + pack avatar
.venv/Scripts/python.exe scripts/run_server.py \
    --config "D:/AI-Interview-Platform-AI/config/livetalking.yaml" \
    --transport webrtc --stun '' --listenport 8010
# client: http://localhost:8010/index.html — bấm Start, gõ câu tiếng Việt
```

`run_server.py` là wrapper bắt buộc: nó vá lỗi `aiortc.and_direction(None)` (crash `/offer`),
đưa ffmpeg của `imageio-ffmpeg` vào PATH để `/record` chạy được, và đổi thư mục làm việc
sang `vendored/LiveTalking`. Vì wrapper có `chdir`, **`--config` phải là đường dẫn tuyệt đối**.

## Avatar người Việt, nói tiếng Việt

| file | nội dung |
|---|---|
| `assets/vn_interviewer_src.png` | chân dung người Việt (ImageGen) dùng làm khuôn mặt |
| `config/livetalking.yaml` | `avatar_id: vn_interviewer_v1`, `tts: edgetts`, `REF_FILE: vi-VN-HoaiMyNeural` |
| `config/livetalking_static.yaml` | bản template tĩnh `vn_interviewer_static`, chỉ để đo kiểm soát |

Tạo pack avatar mới từ một ảnh hoặc video bất kỳ (chạy lúc **đã tắt server** vì phát hiện
mặt chiếm GPU):

```bash
.venv/Scripts/python.exe scripts/make_avatar.py --src assets/vn_interviewer_src.png \
    --id my_avatar                 # có drift nhẹ cho đỡ đóng băng
.venv/Scripts/python.exe scripts/make_avatar.py --src assets/vn_interviewer_src.png \
    --id my_avatar --still         # template tĩnh tuyệt đối (dùng khi đo)
```

## Đo khẩu hình (lip-sync)

```bash
.venv/Scripts/python.exe scripts/p0_controlled_record.py --id demo --idle 4   # im → nói → im
cp vendored/LiveTalking/data/record/<sessionid>.mp4 p0_demo.mp4   # sessionid in ra ở console
.venv/Scripts/python.exe scripts/p0_lipsync_measure.py p0_demo.mp4 vn_interviewer_static \
    docs/events_demo.json
```

Tool đo in ra: dải hàng được tự xác định là miệng, motion giữa 2 frame, **dev** = mức
model vẽ lại so với chính ảnh gốc, biên độ âm thanh theo frame, tương quan dev↔âm thanh
kèm trễ tối ưu, và tỷ lệ nói/im. Kết quả ngày 2026-10-26 và giải thích vì sao phải dùng
template tĩnh: `docs/P0-results.md`.

## Chạy P1 — AI service (cửa sổ riêng, tiến trình avatar phải đang chạy)

```bash
cd D:/AI-Interview-Platform-AI
cp .env.example .env            # .env đã ở .gitignore; không để khóa trong mã nguồn
.venv/Scripts/python.exe -m pip install -r requirements-ai.txt
.venv/Scripts/python.exe run_ai_server.py            # :8020, warmup faster-whisper lúc khởi động
curl http://127.0.0.1:8020/health                    # avatarSessions:-1 là không với tới :8010
```

Ba biến đáng chú ý trong `.env`: `AI_LLM_PROVIDER=template` (offline, không cần khóa — mặc định)
hoặc `openai` kèm `AI_LLM_BASE_URL`/`AI_LLM_MODEL`/`AI_LLM_API_KEY`; `AI_STT_MODEL`
(`small`/`base`/`tiny`, đã đo RTF 0.83/0.29/0.16 trên CPU int8); `AI_SESSION_TTL_MIN` (transcript
chỉ sống trong RAM). Giọng đọc **không** cấu hình ở đây — nó thuộc tiến trình avatar,
xem `config/livetalking.yaml`.

Đo lại toàn bộ số liệu trong `docs/P1-ai-service.md`:

```bash
PYTHONIOENCODING=utf-8 .venv/Scripts/python.exe scripts/p1_ai_service_e2e.py   # phiên thật qua HTTP
PYTHONIOENCODING=utf-8 .venv/Scripts/python.exe scripts/p1_stt_cost.py         # chi phí STT từng model
PYTHONIOENCODING=utf-8 .venv/Scripts/python.exe scripts/p1_tts_spread.py       # mấy giây avatar cất tiếng
```

## API chính của LiveTalking (AI service đã bọc lại; FE chỉ nối WebRTC lấy stream)

- `POST /offer` — body JSON `{sdp, type}`; **server tự sinh và trả về `sessionid`**, không dùng id client gửi lên
- `POST /human` — `{text, type: echo|chat, sessionid}` → EdgeTTS → avatar nói
- `POST /humanaudio` — multipart, **tên field là `file`**, wav 16k mono → avatar nói
- `POST /record` — `{type: start_record|end_record, sessionid}`; file ghi ra `data/record/<sessionid>.mp4` (không có route tải về)
- `POST /is_speaking` — `{sessionid}` → `data: true|false`; **rơi giữa các câu**, muốn biết hết đoạn phải chờ im liên tục
- Mọi route đều trả HTTP 200 kể cả khi lỗi — phải kiểm tra `code` trong body.

## Patch đã đánh dấu trong `vendored/LiveTalking`

Mặc định không sửa code vendored. Bốn file dưới đây là ngoại lệ có chủ đích, **sẽ mất khi pull lại
nguồn** — khôi phục bằng `git -C vendored/LiveTalking checkout -- web/`:

| file | đã đổi | lý do |
|---|---|---|
| `web/index.html` | xóa wordmark "LiveTalking" trên thanh điều hướng; `<title>` → "Phòng phỏng vấn AI"; câu mẫu trong ô text → tiếng Việt | màn demo này ứng viên và hội đồng có thể nhìn thấy, không để logo của engine lộ ra |
| `web/index-en.html` | ba thay đổi như trên, bản tiếng Anh | cùng lý do; trang này được link từ `index.html` |
| `web/admin.html` | bỏ chữ "LiveTalking" trong `<title>` và heading | trang xem số phiên, chiếu khi demo |
| `web/avatar.html` | bỏ chữ "LiveTalking" trong `<title>` và heading | trang tạo pack avatar, mở từ `index.html` |

Các trang test còn lại của upstream (`index-whep.html`, `webrtcapi*.html`, `rtmp*`, `virtualcam`,
`admin-en`, `avatar-en`) vẫn giữ nguyên thương hiệu gốc — ta không dùng tới chúng.

## Trạng thái

- [x] P0 môi trường: venv + torch cu118 + LiveTalking clone
- [x] P0 weights về đúng chỗ
- [x] P0 stub: avatar nói, 25fps stream, VRAM đỉnh 3.7/4GB → `docs/P0-results.md`
- [x] Avatar **người Việt, giọng tiếng Việt** (`vn_interviewer_v1` + `vi-VN-HoaiMyNeural`)
- [x] Đo khẩu hình có kiểm soát: dev nói/im **5.6x**, motion **14.8x**, r(dev↔audio) **0.42**
- [x] P1: AI service FastAPI — 12 route, đã đo end-to-end với LiveTalking thật → `docs/P1-ai-service.md`
- [x] P1: chữ hóa tiếng Việt bằng faster-whisper (RTF 0.83 với `small` khi máy không chạy avatar)
- [ ] P1 mở: STT sai thuật ngữ tiếng Anh + số → FE phải cho ứng viên sửa chữ trước khi chốt
- [ ] P1 mở: avatar và STT cần hai máy (RAM 8GB, GPU đã bị avatar giữ 3716/4096 MiB)
- [ ] P2: FE gọi các route này + relay transcript sang BE spec 012
