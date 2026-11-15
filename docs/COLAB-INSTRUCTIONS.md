# 🚀 Hướng dẫn Deploy AI Avatar Interview từ Repo Local

## ✅ Tình trạng hiện tại

Bạn đã clone xong:
```bash
https://github.com/PNV-do-an/AI-Interview-Platform-AI.git
```

Repository này chứa:
- **LiveTalking avatar engine** (WebRTC video streaming)
- **FastAPI service** (orchestration layer)
- **faster-whisper STT** (speech-to-text)
- **edge-tts TTS** (text-to-speech)

---

## 📁 Files sẵn sàng sử dụng

Trong `/docs/` của backend repo của bạn:

| File | Mô tả | Cách dùng |
|------|-------|-----------|
| **colab-deploy-fixed.ipynb** | Notebook Colab đã fix | Upload & chạy ngay |
| **QUICK-START.md** | Tóm tắt 1 trang | Đọc nhanh overview |
| **DEPLOYMENT_CHECKLIST.md** | Checklist chi tiết | Follow từng bước |
| **colab-deployment-guide.md** | Tutorial đầy đủ | Tham khảo sâu |

---

## 🎯 Các bước thực hiện

### Bước 1: Chuẩn bị ngrok Auth Token (2 phút)

**BẮT BUỘC PHẢI CÓ** trước khi run notebook!

1. Visit: https://dashboard.ngrok.com/get-started/your-authtoken
2. Register/Login (miễn phí)
3. Copy Authtoken (format: `2xXXXXXXXXXXXXXXXXXXXXX`)
4. Lưu vào clipboard

---

### Bước 2: Upload Notebook lên Google Colab

**Cách A: Upload trực tiếp từ local**

```bash
1. Mở: https://colab.research.google.com/
2. Click "Upload" → "Upload notebook"
3. Chọn file: D:\AI-Interview-Platform-BE\docs\colab-deploy-fixed.ipynb
4. Runtime → Change runtime type → Hardware accelerator = GPU (T4)
```

**Cách B: Copy paste cells thủ công** (nếu upload không được)

Mở notebook mới trên Colab và copy từng cell từ `colab-deploy-fixed.ipynb`:
- Cell 1-8 (skip markdown cell đầu tiên)

---

### Bước 3: Chạy Notebook theo thứ tự

#### Cell 1: Setup Docker & GPU (~3 phút)
```python
- Cài đặt Docker
- Cấu hình NVIDIA runtime
- Verify GPU detected
```

**Nếu thấy**: `✅ GPU detected!` → Proceed  
**Nếu thấy**: `⚠️ No GPU detected` → Re-run với T4 GPU runtime

---

#### Cell 2: Clone Repository (~1 phút)

**Hiện tại đang clone từ GitHub:**
```python
!git clone https://github.com/PNV-do-an/AI-Interview-Platform-AI.git
%cd AI-Interview-Platform-AI
```

**🔧 CẬP NHẬT THÀNH LOCAL PATH:**

Thay bằng lệnh này nếu muốn build từ folder local:

```python
# Option 1: Dùng local path đã clone
!cp -r /content/AI-Interview-Platform-AI /content/my-ai-service
%cd /content/my-ai-service

# Option 2: Nếu đã clone sang Google Drive
%cd /content/drive/MyDrive/AI-Interview-Platform-AI
```

Hoặc nếu muốn mount Google Drive để share code giữa các sessions:

```python
from google.colab import drive
drive.mount('/content/drive')
%cd /content/drive/MyDrive/AI-Interview-Platform-AI
```

**Verify:**
```
✅ Found Dockerfile!  ← Phải thấy这个消息
```

---

#### Cell 3: Build Docker Image (~10-15 phút)

```python
!docker build -t hiremind-ai-service .
```

**Thời gian phụ thuộc vào:**
- Internet speed (download dependencies)
- Colab GPU available (T4 is good)
- Cache status (lần sau sẽ nhanh hơn)

**Expected output:**
```
Step 1/15 : FROM nvidia/cuda:11.0-base
...
Successfully built XXXXXXXX
Successfully tagged hiremind-ai-service:latest
```

---

#### Cell 4: Run Container (~40 giây khởi động + 35s warmup)

```python
docker run -d --name hiremind-ai --gpus all \
  -p 8010:8010 -p 8020:8020 \
  -e AI_AVATAR_SERVER=http://livetalking:8010 \
  -e AI_ALLOWED_ORIGIN=* \
  hiremind-ai-service
```

**Chờ 35 giây** cho LiveTalking model warm up!

**Kiểm tra:**
```
✅ Health check passed!
   STT Model: small
```

---

#### ⚡ Cell 5: Paste Ngrok Token (QUAN TRỌNG!)

```python
NGROK_AUTH_TOKEN = 'YOUR_NGROK_AUTHTOKEN_HERE'
                ^^^^^^^^^^^^^^^^^^^^
                THAY BẰNG TOKEN CỦA BẠN!
```

**Paste token format:**
```python
NGROK_AUTH_TOKEN = '2xAbCdEfGhIjKlMnOpQrStUvWxYz...'
```

**Sau khi paste đúng**, bạn sẽ thấy:
```
✅ AI Service URL: https://xxxx.ngrok-free.app
✅ WebRTC Stream URL: https://xxxx-2.ngrok-free.app
```

→ **COPY NGAY 2 URLs NÀY!**

---

#### Cell 6: Test Endpoints (~2 phút)

Tự động test 2 endpoints chính:
- GET `/health` → Must be 200 OK
- GET `/api/admin/sessions` → Must be 200 OK

**Expected:**
```
✅ GET /health PASSED
✅ GET /api/admin/sessions PASSED
Results: 2/2 tests passed
```

---

#### Cell 7: Get Frontend Config

Output sẽ hiển thị:
```
Frontend Configuration:
----------------------------------------------------------------------
REACT_APP_API_URL=https://aiinterview-vn.onrender.com/api/v1
REACT_APP_AI_SERVICE_URL=https://xxxx.ngrok-free.app
----------------------------------------------------------------------

Update Vercel Environment Variables:
1. Go to: https://vercel.com/dashboard
2. Settings → Environment Variables
3. Add REACT_APP_AI_SERVICE_URL=<above_url>
4. Redeploy: vercel deploy --prod
```

---

#### Cell 8: Monitor & Troubleshooting

Hiển thị:
- Container resource usage (CPU/RAM)
- GPU memory usage
- Recent logs

Quick fixes tips if something goes wrong.

---

## 🔧 Tích hợp với Frontend

Sau khi có ngrok URL, update Vercel env var:

### Option A: Via Dashboard

1. https://vercel.com/dashboard
2. Select project (frontend of AI Interview)
3. Settings → Environment Variables
4. Add new variable:
   ```
   Name: REACT_APP_AI_SERVICE_URL
   Value: https://xxxx.ngrok-free.app
   Environments: Production, Preview, Development
   ```
5. Redeploy app

### Option B: Via CLI

```bash
# Login first if not logged in
vercel login

# Set environment variable
vercel env add REACT_APP_AI_SERVICE_URL
# Enter the ngrok URL when prompted

# Deploy
vercel deploy --prod
```

---

## 🎬 Testing Flow

Sau khi config frontend, test toàn bộ flow:

### 1. Start Avatar Interview

```typescript
POST /api/v1/mock-interviews
Body: { mode: 'AVATAR', jobId: '123' }
Response: { sessionId, questions: [] }
```

### 2. Establish WebRTC Connection

```typescript
const sdpOffer = await generateWebRTCOffer(); // Browser side

POST https://ngrok-url/api/v1/ai/avatar/offer
Body: { offerSdp: sdpOffer }
Response: { answerSdp, avatarSessionId }
```

### 3. Avatar Speaks Question

- Avatar xuất hiện trên video
- Lip-sync hoạt động
- Giọng nói tiếng Việt (HoaiMy)
- Có audio stream

### 4. User Respond (Audio)

```typescript
// Record audio from microphone
const audioBlob = await recordAudio(); // 6 seconds typical

POST /api/v1/ai/session/{id}/answer/audio
multipart/form-data: { file: audioBlob }
Response: { 
  transcript: "Em xin chào...",
  confirmed: false  // có thể sửa lại
}
```

### 5. Review & Confirm Transcript

- User xem transcript do faster-whisper chuyển đổi
- Sửa lỗi nếu ASR sai (tiếng Việt dễ nhầm thuật ngữ)
- Submit `confirm: true` để chốt

### 6. Continue Conversation

Avatar sinh câu hỏi tiếp theo → Repeat steps 3-5

### 7. Finish Interview

```typescript
POST /api/v1/mock-interviews/{id}/finish
Response: { completedAt, overallScore }
```

### 8. View Report

```typescript
GET /api/v1/mock-interviews/{id}/report
Response: {
  chuyenMon: 85,
  duAn: 78,
  tuDuy: 92,
  truyenDat: 88,
  mucKhop: 95,
  overallScore: 87.6,
  strengths: [...],
  improvements: [...]
}
```

---

## 🆘 Troubleshooting Common Issues

### ❌ Error: "No GPU detected" trong Cell 1

**Triệu chứng:**
```
⚠️ No GPU detected or Docker not configured properly
```

**Giải pháp:**
1. Runtime → Factory reset runtime
2. chọn "Hardware accelerator" = **GPU T4 Tensor Core**
3. Re-run Cell 1

---

### ⚠️ Error: "Port already in use"

**Triệu chứng:**
```
Error starting container: port is already allocated
```

**Giải pháp:**
```python
# Kill existing containers
!docker rm -f hiremind-ai || true
!docker ps -a | grep exited
!docker rmi hiremind-ai-service || true
```

Then re-run Cells 3 & 4.

---

### ❌ Error: "Invalid ngrok auth token"

**Triệu chứng:**
```
auth fails with error message
```

**Giải pháp:**
1. Verify token tại: https://dashboard.ngrok.com
2. Ensure format correct: `2xXXXXXXXXXXXXXXXXXXXXX`
3. Check internet connection stable
4. Regenerate token if expired

---

### 🐢 Performance: Service very slow (>30s response)

**Nguyên nhân:** RAM/CPU quá tải trên Colab Free Tier

**Giảm load:**

Edit file `.env` trong repository trước khi build:

```bash
AI_STT_MODEL=tiny  # fastest but less accurate
# OR
AI_STT_MODEL=base  # balanced speed/accuracy
```

Or edit `requirements-ai.txt`:
```bash
pip install faster-whisper==0.9.0  # lighter version
```

Then rebuild image (Cell 3).

---

### 🌐 CORS Error in Browser Console

**Triệu chứng:**
```
Access to fetch has been blocked by CORS policy
```

**Giải pháp:**
1. Check container env vars: `AI_ALLOWED_ORIGIN=*`
2. Re-run container:
   ```bash
   !docker restart hiremind-ai
   ```

---

### 💥 Session Timeout after ~12 hours

Colab free tier disconnects after ~12 hours continuous use.

**Keep session alive:**

Run Cell 10 (keep-alive ping):
```python
while True:
    requests.get('http://localhost:8020/health')
    time.sleep(300)  # 5 minutes
```

OR manually reconnect:
1. Re-open same notebook
2. Re-run Cell 3 (build image can reuse cache)
3. Re-run Cell 4 (start container)
4. Re-run Cell 5 (new ngrok URL will be generated)
5. Update frontend env var with NEW ngrok URL

---

## 📊 Cost & Limitations

### Google Colab Free Tier

| Resource | Limit | Your Usage |
|----------|-------|------------|
| GPU Hours | ~12h/session | Depends on session length |
| Storage | Ephemeral | Lost on disconnect |
| Bandwidth | Limited | Within fair use policy |

### Ngrok Free Tier

| Resource | Limit |
|----------|-------|
| Bandwidth | 60GB/month |
| Concurrent tunnels | 1 (covers both ports 8010+8020) |
| Public URL | Changes each reconnect |

---

## 🔄 Migration Path (Production)

### Phase 1: Testing (Now) ✅
**Status:** Google Colab Free  
**Cost:** $0/month  
**Best for:** Demo, testing with friends

### Phase 2: Beta Production
**Option A: Google Cloud Spot Instance**
```bash
gcloud compute instances create hiremind-ai \
  --machine-type=n1-standard-2 \
  --accelerator=count=1,type=nvidia-tesla-t4 \
  --zone=us-central1-a
```
**Cost:** ~$110/month  
**Benefit:** Stable uptime, dedicated GPU

**Option B: Render Container**
Deploy Docker image directly to Render's GPU support
**Cost:** ~$75/month  
**Benefit:** Auto-scaling ready

### Phase 3: Full Production
**Multiple GPU instances + Load balancer + Own domain**  
**Cost:** ~$360+/month  
**Benefit:** Enterprise SLA, auto-scaling

---

## 📝 Next Steps

1. ✅ Run Cell 1-8 in sequence on Colab
2. ✅ Copy ngrok URL from Cell 5
3. ✅ Update Vercel environment variables
4. ✅ Redeploy frontend: `vercel deploy --prod`
5. ✅ Test full interview flow
6. ✅ Share with team/users for feedback

---

## 💡 Pro Tips

### Save Work & State
- Bookmark your Colab session URL
- Save ngrok URLs somewhere (they change on reconnect)
- Export any important data before session ends

### Optimize Performance
- Keep Docker image cached by not deleting it
- Use smaller STT model (`tiny` or `base`) if speed critical
- Reduce max concurrent sessions in config

### Security Notes
- Don't expose ngrok URL publicly without rate limiting
- Consider adding basic auth for production
- Monitor bandwidth usage on ngrok dashboard

---

## 📞 Support Resources

### Internal Docs
- **Full tutorial**: `/docs/colab-deployment-guide.md`
- **Checklist**: `/docs/DEPLOYMENT_CHECKLIST.md`
- **Backend spec**: `/specs/012-avatar-interview-api/spec.md`
- **AI service docs**: `/docs/P1-ai-service.md`

### External Links
- ngrok dashboard: https://dashboard.ngrok.com
- Colab help: https://colab.research.google.com/notebooks/intro.ipynb
- LiveTalking repo: https://github.com/lipku/LiveTalking

---

**Ready to deploy?** 👉 Start with **QUICK-START.md** then follow **DEPLOYMENT_CHECKLIST.md**

Good luck! 🚀
