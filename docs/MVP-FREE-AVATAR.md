# MVP Free: Phỏng vấn AI có avatar (không GPU, không Colab)

## Mục tiêu

Chạy luồng phỏng vấn AI **free hoàn toàn** trên BE hiện có, không cần GPU/Colab/LiveTalking:

- Sinh câu hỏi: Gemini/OpenAI free tier qua BE (`OpenAiClient`).
- STT: Whisper/OpenAI hoặc Gemini qua BE.
- TTS: Browser `speechSynthesis` (free).
- Avatar: ảnh avatar + animation miệng đơn giản (CSS/JS), không lip-sync thật.
- BE vẫn lưu session/transcript/report theo spec 012.

## Kiến trúc

```
FE (React)
  ├─ Browser TTS: đọc câu hỏi
  ├─ Avatar image + mouth animation (CSS)
  ├─ gọi BE /api/v1/ai-interview/question
  ├─ ghi âm -> gọi BE /api/v1/ai-interview/transcribe
  ├─ relay transcript -> BE /api/v1/mock-interviews/{id}/answer
  └─ finish -> BE /api/v1/mock-interviews/{id}/finish
BE (Spring Boot)
  ├─ OpenAiClient (provider=gemini/openai)
  ├─ lưu session/transcript/report (spec 012)
  └─ không nhận audio/video lâu dài
```

## Endpoint mới (free MVP)

### 1. Sinh câu hỏi
`POST /api/v1/ai-interview/question`

Body:
```json
{
  "jobTitle": "Java Backend Developer",
  "jdText": "...",
  "history": [
    { "questionText": "...", "answerText": "..." }
  ]
}
```

Response:
```json
{ "questionText": "..." }
```

### 2. Chuyển audio thành text
`POST /api/v1/ai-interview/transcribe` (multipart/form-data, field `file`)

Response:
```json
{ "text": "..." }
```

### 3. Chấm rubric free
`POST /api/v1/ai-interview/evaluate`

Body:
```json
{
  "jobTitle": "...",
  "jdText": "...",
  "transcript": [
    { "questionText": "...", "answerText": "..." }
  ]
}
```

Response:
```json
{
  "rubric": { "chuyenMon": 80, "duAn": 75, "tuDuy": 85, "truyenDat": 70, "mucKhop": 90 },
  "strengths": "...",
  "improvements": "...",
  "rubricVersion": "free-mvp-2026-11"
}
```

## Luồng FE

1. `POST /api/v1/mock-interviews` với `mode=AVATAR`, `jobId`.
2. Lặp:
   - gọi `/api/v1/ai-interview/question` để lấy câu hỏi.
   - Browser TTS đọc câu hỏi.
   - Avatar ảnh + animation miệng.
   - Người dùng trả lời bằng micro/text.
   - Nếu audio: gọi `/api/v1/ai-interview/transcribe`.
   - Relay sang BE `/api/v1/mock-interviews/{id}/answer`.
3. Kết thúc: `POST /api/v1/mock-interviews/{id}/finish`.
4. Nếu muốn chấm bằng LLM: gọi `/api/v1/ai-interview/evaluate`, sau đó gửi kết quả sang BE `/api/v1/mock-interviews/{id}/evaluation`.

## Cấu hình free

Ví dụ dùng Gemini free tier:

```yaml
ai:
  provider: gemini
  api-key: ${GEMINI_API_KEY}
  base-url: https://generativelanguage.googleapis.com/v1beta
  model: gemini-1.5-flash
  eval-model: gemini-1.5-flash
  whisper-model: whisper-1
```

Hoặc OpenAI free/low-cost:
```yaml
ai:
  provider: openai
  api-key: ${OPENAI_API_KEY}
  base-url: https://api.openai.com/v1
  model: gpt-4o-mini
  eval-model: gpt-4o-mini
  whisper-model: whisper-1
```

## Giới hạn

- Không có lip-sync thật.
- Chất lượng câu hỏi/phân tích phụ thuộc model free tier.
- Free tier có quota và rate limit.
- Avatar chỉ là ảnh + animation đơn giản.

## Vì sao không cần đổi BE contract

Spec 012 vẫn giữ nguyên:
- BE không nhận audio/video lâu dài.
- Transcript relay bằng JWT ứng viên.
- Report tự sinh hoặc nhận rubric từ FE/AI.
- `overallScore` của session AVATAR vẫn `null`.