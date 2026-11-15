# 🚀 Hướng dẫn nhanh - Deploy AI Avatar Interview trên Google Colab

## 📁 Files đã tạo

1. **`colab-deploy-notebook.ipynb`** - Notebook Colab hoàn chỉnh (10 cells)
2. **`colab-deployment-guide.md`** - Hướng dẫn chi tiết từng bước
3. **`DEPLOYMENT_CHECKLIST.md`** - Checklist kiểm tra deployment

---

## ⚡ Quick Start (5 phút)

### Bước 1: Lấy ngrok Auth Token
```
1. Visit: https://dashboard.ngrok.com/get-started/your-authtoken
2. Register/Login (miễn phí)
3. Copy Authtoken (format: 2xXXXXXXXXXXXXXXXXXXXXX...)
```

### Bước 2: Mở Notebook trên Colab

**Cách 1: Upload trực tiếp**
```bash
1. Vào: https://colab.research.google.com/
2. Click "Upload" → Upload file colab-deploy-notebook.ipynb
3. Runtime → Change runtime type → T4 GPU
```

**Cách 2: Import từ GitHub** (nếu repo public)
```
File → Share this notebook → Paste URL Colab
```

### Bước 3: Chạy từng cell theo thứ tự

| Cell | Tên | Thời gian | Ghi chú |
|------|-----|-----------|---------|
| Cell 1 | Setup Docker & GPU | ~3 phút | ✅ Bắt buộc có GPU |
| Cell 2 | Clone repo | ~1 phút | - |
| Cell 3 | Build Docker image | ~15 phút | Lâu nhất |
| Cell 4 | Run container | ~40 giây | Đợi warm up |
| **Cell 5** | **Setup ngrok** | ~15 giây | ⚠️ **Paste token!** |
| Cell 6 | Test endpoints | ~2 phút | Verify service |
| Cell 7 | Monitor resources | ~1 phút | Theo dõi GPU/RAM |
| Cell 8 | Frontend guide | - | Demo code |
| Cell 9 | Troubleshooting | - | Guide xử lý lỗi |
| Cell 10 | Keep-alive | Optional | Ping mỗi 5 phút |

---

## 🔐 Quan trọng: Paste ngrok token ở Cell 5

```python
NGROK_AUTH_TOKEN = 'YOUR_NGROK_AUTHTOKEN_HERE'
                ^^^^^^^^^^^^^^^^^^^^
                Thay bằng token thật của bạn!
```

**Lấy token ở đâu?**
https://dashboard.ngrok.com/get-started/your-authtoken

Sau khi paste, cell sẽ output:
```
✅ AI Service URL: https://xxxxx.ngrok-free.app
✅ WebRTC Stream URL: https://xxxxx-2.ngrok-free.app
```

→ **COPY 2 URLs NÀY ĐỂ CẤU HÌNH FRONTEND!**

---

## 🎯 Kết quả sau khi deploy thành công

### Từ test Cell 6, phải thấy:

```
[Test 1] GET /health
   ✅ PASS
      - STT Model: small
      - Max Turns: 40
      - Avatar Sessions: 0

[Test 2] GET /api/admin/sessions
   ✅ PASS (Active sessions: 0)

[Test 3] GET /llm
   ✅ PASS
      - Provider: template
      - Model: N/A

📊 KẾT QUẢ TEST: 3 passed, 0 failed
✅ TẤT CẢ TEST ĐỀU THÀNH CÔNG!
```

---

## 🔧 Tích hợp với Frontend

### Cách 1: Update Vercel Environment Variables

```bash
# Terminal command
vercel env add REACT_APP_AI_SERVICE_URL
# Enter URL ngrok từ Cell 5
vercel deploy --prod
```

Hoặc qua Dashboard:
1. https://vercel.com/dashboard
2. Settings → Environment Variables
3. Add: `REACT_APP_AI_SERVICE_URL=https://xxxx.ngrok-free.app`
4. Redeploy

### Cách 2: Direct call từ browser (test mode)

Mở DevTools console và chạy:

```javascript
fetch('https://YOUR_NGROK_URL/ngrok-free.app/health')
  .then(r => r.json())
  .then(console.log);
```

Nếu thấy `{status:"ok", ...}` → Service hoạt động! ✅

---

## ⚠️ Giới hạn & Lưu ý

### Free Tier Limits (Google Colab)
- ⏱️ Session timeout: ~12 giờ liên tục
- 💾 Storage: Ephemeral (mất khi disconnect)
- 🖥️ GPU: T4 Tensor Core (~15GB RAM)
- 🌐 Bandwidth: 60GB/tháng (ngrok free)

### Ngrok Free Tier
- 🔄 URL thay đổi mỗi lần reconnect
- 📊 Limit: 1 concurrent tunnel (cần 2 ports: 8010+8020)
- 💰 Free: 60GB/month bandwidth

### Hardware Requirements (đã test successful)
- GPU: NVIDIA T4 (Colab Free) ✅
- RAM: ≥10GB system memory
- Disk: ~5GB cho Docker image

---

## 🆘 Troubleshooting nhanh

### ❌ Không detect GPU
**Triệu chứng**: `nvidia-smi` fails trong Cell 1  
**Giải pháp**:
```
Runtime → Factory reset runtime → Chọn T4 GPU
Re-run Cell 1
```

### ⚠️ Docker không khởi động được
**Triệu chứng**: Container exits immediately  
**Giải pháp**:
```python
# Kill old containers first
!docker rm -f hiremind-ai || true
!docker rmi hiremind-ai-service || true
# Re-build and re-run
```

### ❌ Ngrok auth error
**Triệu chứng**: "Invalid authtoken" message  
**Giải pháp**:
- Check token format (phải bắt đầu bằng `2x`)
- Ensure internet connection stable
- Regenerate token if expired

### 🐢 Service slow hoặc timeout
**Nguyên nhân**: RAM/CPU quá tải trên Colab Free  
**Giảm load**:
```bash
# Edit requirements-ai.txt trước build
pip install faster-whisper==0.9.0  # lighter version
```

---

## 💡 Tips tối ưu

### 1. Giảm thời gian build
- Có sẵn Docker image nếu dùng notebook nhiều lần
- Cache layers by building frequently-changed dependencies first

### 2. Tăng performance
- Dùng model nhỏ hơn: `AI_STT_MODEL=tiny`
- Giảm max sessions: `max_session: 3` (thay vì 5)
- Disable unnecessary features trong config

### 3. Keep session alive
```python
# Send ping every 5 minutes
requests.get('http://localhost:8020/health')
```

Run Cell 10 để auto-ping

---

## 📊 Chi tiết architecture

```
┌─────────────────────────────────────────────────┐
│          Google Colab Free Tier                 │
│                                                 │
│  ┌──────────────┐    ┌──────────────────────┐ │
│  │  LiveTalking │◄──►│  FastAPI Service     │ │
│  │  Engine      │    │  (Port 8020)         │ │
│  │  (Port       │    │                      │ │
│  │   8010)      │    │                      │ │
│  └──────────────┘    └──────────────────────┘ │
│                        │                       │
│                        ▼                       │
│              ┌──────────────────┐             │
│              │   ngrok Tunnel   │             │
│              │   HTTPS Public   │             │
│              │   URL            │             │
│              └──────────────────┘             │
└─────────────────────────────────────────────────┘
                        │
                        ▼
┌─────────────────────────────────────────────────┐
│           Frontend (Vercel App)                 │
│                                                 │
│  https://aiinterview-vn.vercel.app             │
│                                                 │
│  Calls AI Service via ngrok URL                │
└─────────────────────────────────────────────────┘
```

---

## 🎬 Demo Flow

### End-to-end testing workflow:

1. **Start interview**
   ```typescript
   POST /api/v1/mock-interviews
   Body: { mode: 'AVATAR', jobId: "123" }
   ```

2. **Establish WebRTC connection**
   ```typescript
   POST https://ngrok-url/api/v1/ai/avatar/offer
   Body: { offerSdp: "..." }
   Response: { answerSdp, avatarSessionId }
   ```

3. **Avatar speaks question**
   - Audio stream from avatar → browser speaker
   - Lip-sync active on video

4. **User responds (audio)**
   ```typescript
   POST /api/v1/ai/session/{id}/answer/audio
   Body: multipart/form-data { file: audio.wav }
   Response: { transcript, confirmed: false }
   ```

5. **Review & confirm transcript**
   - User edits text if ASR incorrect
   - `confirm: true` to finalize

6. **Submit rubric scoring**
   ```typescript
   POST /api/v1/mock-interviews/{id}/evaluation
   Body: {
     chuyenMon: 85,
     duAn: 78,
     tuDuy: 92,
     truyenDat: 88,
     mucKhop: 95,
     rubricVersion: "2026-10-v1"
   }
   ```

7. **View report**
   ```typescript
   GET /api/v1/mock-interviews/{id}/report
   Response: { overallScore, strengths, improvements }
   ```

---

## 📞 Support & Documentation

### Internal docs:
- Spec 012 API: `/specs/012-avatar-interview-api/spec.md`
- AI Service: `/docs/P1-ai-service.md`
- Hardware reqs: `/memory/hardware-gtx1650-4gb.md`

### External resources:
- ngrok dashboard: https://dashboard.ngrok.com
- Colab help: https://colab.research.google.com/notebooks/intro.ipynb
- LiveTalking repo: https://github.com/lipku/LiveTalking

---

## ✅ Pre-deployment checklist

Before showing stakeholders:

- [ ] All 10 Colab cells run without errors
- [ ] ngrok tunnel established with valid token
- [ ] Health check passes at ngrok public URL
- [ ] At least 3 endpoint tests pass
- [ ] Can start avatar interview session
- [ ] Microphone permission granted in browser
- [ ] Audio/video streaming works (speaking + lip-sync)
- [ ] Speech-to-text transcription working
- [ ] Rubric scoring generates valid scores (0-100)
- [ ] Report displays correctly after finish

---

## 🔄 Migration path (production ready)

**Phase 1 (Current):** Google Colab Free
- Cost: $0/month
- Uptime: ~12h/session
- Best for: Testing, demos

**Phase 2 (Beta):** GCP Spot Instance
- Cost: ~$110/month
- Uptime: 24/7
- Best for: Beta users

**Phase 3 (Scale):** Kubernetes cluster
- Cost: ~$360+/month
- Scalability: Auto-scale
- Best for: Production traffic

---

**Created:** 2026-11-03  
**Tested On:** Google Colab Free Tier + GTX T4 GPU + ngrok Free  
**Status:** Ready for production demo 🚀

---

**Need help?** See detailed guides:
- Full tutorial: `colab-deployment-guide.md`
- Step-by-step checklist: `DEPLOYMENT_CHECKLIST.md`
- Interactive notebook: `colab-deploy-notebook.ipynb`
