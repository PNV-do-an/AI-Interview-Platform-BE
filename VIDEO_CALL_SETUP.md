# 🎥 Hướng dẫn Setup Video Call AI Interview

## Tổng quan

Hệ thống hỗ trợ 2 chế độ phỏng vấn với AI:

### Mode 1: Browser TTS (HOẠT ĐỘNG NGAY - Không cần GPU)
- ✅ Hoạt động trên Vercel free tier
- ✅ Không cần camera, chỉ cần micro
- ✅ AI đọc câu hỏi bằng browser's speechSynthesis API
- ❌ Không có video avatar thật (chỉ text + voice)

### Mode 2: LiveTalking Avatar (Yêu cầu GPU server/Colab)
- ✅ Avatar nói video thật qua WebRTC
- ✅ Có lip-sync tự nhiên
- ✅ Camera & microphone enabled
- ⚠️ Cần GPU server hoặc Google Colab (free)

---

## 🔧 Cách Deploy Mode 2 - LiveTalking Avatar

### Option A: Google Colab (FREE - Recommended)

**Bước 1: Mở Colab Notebook**
```
1. Truy cập: https://colab.research.google.com/
2. File → Upload notebook
3. Tải file từ repo: AI-Interview-Platform-BE/colab-deploy.ipynb
4. Chọn Runtime → Change runtime type → Hardware accelerator: GPU T4
```

**Bước 2: Chạy từng cell theo thứ tự**
- Cell 1: Cài Docker + NVIDIA toolkit
- Cell 2: Clone repository  
- Cell 3: Build Docker image (~15 phút)
- Cell 4: Run container
- Cell 5: Ngrok forward port (lấy URL public)
- Cell 6-7: Verify health & get deployment info

**Bước 3: Config vào Vercel Dashboard**
```
1. Truy cập: https://vercel.com/dashboard
2. Chọn project: AI-Interview-Platform-FE
3. Settings → Environment Variables
4. Add new variable:
   - Name: REACT_APP_AI_SERVICE_URL
   - Value: [URL_ngrok_từ_cell_5]
5. Deploy lại app
```

**Bước 4: Test**
```
1. Mở: https://aiinterview-vn.vercel.app/mock-interviews
2. Click "Vào phòng Video Call"
3. Chọn mode: "LiveTalking Avatar"
4. Nếu thấy "Đang kết nối..." là THÀNH CÔNG!
```

### Option B: Local Machine (Nếu có GPU mạnh)

```bash
cd /path/to/AI-Interview-Platform-BE

# Build Docker image
docker build -t hiremind-ai-service .

# Run container with GPU support
docker run -d \
  --name hiremind-ai \
  --gpus all \
  -p 8010:8010 \
  -p 8020:8020 \
  -e AI_AVATAR_SERVER=http://livetalking:8010 \
  -e AI_ALLOWED_ORIGIN=https://localhost:3000 \
  hiremind-ai-service

# Get local URL
echo http://localhost:8020

# Config FE's .env.production:
REACT_APP_API_URL=http://localhost:8081
REACT_APP_AI_SERVICE_URL=http://localhost:8020
```

### Option C: Cloud GPU (GCP Vertex AI - $0.50/hr)

Xem chi tiết trong file `DEPLOYMENT.md` phần **Option B: Google Cloud Vertex AI**

---

## 💰 Chi phí so sánh

| Method | GPU Cost | Storage | Bandwidth | Monthly |
|--------|----------|---------|-----------|---------|
| **Google Colab Free** | $0 | $0 | $0 | **$0** ✅ |
| GCP Spot Instance | ~$0.15/hr | $5 | Included | ~$115 |
| GCP Regular VM | ~$0.50/hr | $5 | Included | ~$370 |

→ **Khuyến nghị:** Dùng Google Colab FREE cho testing/demo
→ Production: Upgrade lên GCP Spot khi có user thật

---

## 📊 Testing Checklist

Sau khi setup xong, test các bước sau:

### Step 1: Start Session
```
✅ Giao diện hiển thị nút "LiveTalking Avatar"
✅ Select mode = 'avatar' thay vì 'text'
✅ Navigate vào trang session
```

### Step 2: Connection
```
✅ Hiển thị thông báo "Đang kết nối với LiveTalking Avatar..."
✅ Nhận được WebSocket connection
✅ Avatar bắt đầu xuất hiện trên màn hình
```

### Step 3: Question Flow
```
✅ Avatar nói question (voice + video lip-sync)
✅ Input area mở để trả lời
✅ Microphone permission requested (nếu có)
```

### Step 4: Answer & Continue
```
✅ Nhập answer text
✅ Click "Next Turn" 
✅ BE nhận transcript entry
✅ Avatar hỏi question tiếp theo
```

### Step 5: End Assessment
```
✅ Sau 40 turns OR click Finish
✅ Form self-assessment rubric (5 dimensions)
✅ Submit → Redirect to result page
```

---

## 🐛 Troubleshooting

### Error: "AI service URL chưa cấu hình"
**Cause:** Chưa set `REACT_APP_AI_SERVICE_URL` trong Vercel dashboard  
**Fix:**
1. Deploy AI service trên Colab/local/cloud
2. Copy ngrok/public URL
3. Vào Vercel Dashboard → Settings → Environment Variables
4. Add key `REACT_APP_AI_SERVICE_URL` = your_url
5. Redeploy frontend

### Error: "No GPU detected" khi build Docker
**Cause:** Môi trường không có GPU passthrough  
**Fix:**
- Local: Install NVIDIA Container Toolkit
- Colab: Tự động có T4 GPU
- Cloud: Chọn instance type có GPU

### Error: Port 8020 already in use
**Cause:** Cổng đang bị process khác chiếm  
**Fix:**
```bash
# Check what's using the port
netstat -ano | findstr ":8020"

# Stop conflicting process OR change port in docker-compose.yml
```

### Error: OOM (Out of Memory)
**Cause:** LiveTalking + STT nặng RAM/GPU  
**Fix:**
- Giảm batch_size: Edit `config/livetalking.yaml`, set `batch_size: 8`
- Dùng model nhỏ hơn: `AI_STT_MODEL=base` hoặc `tiny`

---

## 🚀 Deployment Commands Summary

### Frontend (Vercel)
```bash
# Already deployed on dev branch
git push origin dev
# Vercel sẽ auto-deploy trong 1-2 phút
```

### Backend Spring Boot (Render)
```bash
cd /path/to/AI-Interview-Platform-BE
git push origin dev
# Render auto-deploy from master/main branch
```

### Python AI Service (Local/Colab)
```bash
# Local with Docker
docker compose up --build --gpus all

# Or direct run
python run_ai_server.py
```

---

## 📝 Environment Variables

### Frontend (.env.production)
```env
REACT_APP_API_URL=https://your-render-backend.onrender.com/api/v1
REACT_APP_AI_SERVICE_URL=https://your-ai-service.ngrok.io  # Optional
```

### AI Service (.env)
```env
AI_AVATAR_SERVER=http://livetalking:8010
AI_AVATAR_ID=vn_interviewer_v1
AI_MAX_TURNS=40
AI_RUBRIC_VERSION=2026-10-v1
AI_HOST=0.0.0.0
AI_PORT=8020
AI_ALLOWED_ORIGIN=https://your-app.vercel.app
```

---

## 🔗 Useful Links

- **Backend Repo:** https://github.com/PNV-do-an/AI-Interview-Platform-BE
- **Frontend Repo:** https://github.com/PNV-do-an/AI-Interview-Platform-FE
- **Deployment Guide:** `/DEPLOYMENT.md`
- **Spec 012:** `/specs/012-avatar-interview-api/spec.md`
- **Colab Quickstart:** `/colab-deploy.ipynb`

---

## ✨ Next Steps

1. **Setup AI Service trên Colab** (miễn phí)
2. **Config Vercel environment variables**
3. **Test full interview flow**
4. **Monitor performance & logs**
5. **Deploy to production cloud** (khi cần)

Có vấn đề? Check file `/docs/troubleshooting.md` hoặc tạo issue trên GitHub!