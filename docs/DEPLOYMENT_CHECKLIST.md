# 📋 Checklist Deploy AI Avatar Interview trên Colab + ngrok

## ✅ Pre-Deployment (Làm trước khi vào Colab)

### 1. Tạo ngrok Auth Token (BẮT BUỘC)
- [ ] Visit: https://dashboard.ngrok.com/get-started/your-authtoken
- [ ] Register/Login (miễn phí)
- [ ] Copy **Authtoken** (dạng: `2xXXXXXXXXXXXXXXXXXXXXX...`)
- [ ] Lưu vào clipboard để paste vào Cell 5

### 2. Chuẩn bị Frontend Configuration
- [ ] Kiểm tra URL BE backend hiện tại: https://aiinterview-vn.onrender.com/api/v1
- [ ] Chuẩn bị Vercel Dashboard access
- [ ] Note environment variables cần update

---

## 🚀 Deployment Steps (Trong Google Colab)

### Step 1: Install Dependencies (~3 phút)
```python
# Cell 1 trong notebook
- Chờ cài Docker và cấu hình GPU
- Check nvidia-smi → thấy GPU detected ✅
```
- [ ] GPU detected thành công? ⬜ Yes / ⬜ No
- [ ] Docker daemon chạy? ⬜ Yes / ⬜ No
- [ ] pip packages installed? ⬜ Yes / ⬜ No

### Step 2: Clone Repository (~1 phút)
```python
# Cell 2
git clone https://github.com/PNV-do-an/AI-Interview-Platform-AI.git
```
- [ ] Clone thành công? ⬜ Yes / ⬜ No
- [ ] Có file Dockerfile? ⬜ Yes / ⬜ No
- [ ] cd AI-Interview-Platform-AI vào đúng folder? ⬜ Yes / ⬜ No

### Step 3: Build Docker Image (~10-15 phút)
```python
# Cell 3
docker build -t hiremind-ai-service .
```
- [ ] Build hoàn tất (không có error)? ⬜ Yes / ⬜ No
- [ ] docker images show hiremind-ai-service? ⬜ Yes / ⬜ No

### Step 4: Run Container (~35 giây khởi động)
```python
# Cell 4
docker run -d --name hiremind-ai --gpus all \
  -p 8010:8010 -p 8020:8020 \
  -e AI_AVATAR_SERVER=http://livetalking:8010 \
  -e AI_ALLOWED_ORIGIN=* \
  hiremind-ai-service
```
- [ ] Container starts thành công? ⬜ Yes / ⬜ No
- [ ] Logs hiển thị "Started"? ⬜ Yes / ⬜ No
- [ ] Health check pass (http://localhost:8020/health)? ⬜ Yes / ⬜ No

**Lưu**: Bước này cần kiên nhẫn vì LiveTalking model warmup mất ~30 giây

### Step 5: Setup ngrok Tunnel (~15 giây)
```python
# Cell 5
NGROK_AUTH_TOKEN = 'YOUR_NGROK_AUTHTOKEN_HERE'  # ← Paste token ở đây!
public_url = ngrok.connect(8020)
ngrok_avatar = ngrok.connect(8010)
```
- [ ] Paste đúng ngrok authtoken? ⬜ Yes / ⬜ No
- [ ] AI_SERVICE_URL hiển thị? ⬜ Yes / ⬜ No
- [ ] Coppy URLs:
  - [ ] Forward API: `https://xxxx.ngrok-free.app`
  - [ ] Avatar Stream: `https://xxxx-2.ngrok-free.app` (port 8010)

### Step 6: Test Endpoints (~2 phút)
```python
# Cell 6
Test health, admin/sessions, llm endpoints
```
- [ ] GET /health → 200 OK? ⬜ Yes / ⬜ No
- [ ] GET /api/admin/sessions → 200 OK? ⬜ Yes / ⬜ No  
- [ ] Tests passed: X/X? ⬜ Yes / ⬜ No

---

## 🔧 Frontend Integration (Sau khi có ngrok URL)

### Update Vercel Environment Variables

#### Cách 1: Qua Dashboard
1. [ ] Vào https://vercel.com/dashboard
2. [ ] Chọn project frontend (nếu có)
3. [ ] Settings → Environment Variables
4. [ ] Add new variable:
   ```
   REACT_APP_AI_SERVICE_URL=https://YOUR_NGROK_URL_FROM_STEP_5
   ```
5. [ ] Save
6. [ ] Redeploy

#### Cách 2: Qua CLI (nếu đã login)
```bash
vercel env add REACT_APP_AI_SERVICE_URL
# Enter giá trị ngrok URL
vercel deploy --prod
```

### Verify Configuration

[ ] Reload trang frontend: https://aiinterview-vn.vercel.app
[ ] Kiểm tra browser console (F12):
  - [ ] Không có CORS errors? 
  - [ ] Request đến ngrok URL thành công?
  - [ ] Response từ AI service trả về hợp lệ?

---

## 🎯 Testing Flow (End-to-End)

### Scenario 1: Avatar Video Call
1. [ ] Navigate đến màn phỏng vấn avatar
2. [ ] Click "Start Interview" (mode: AVATAR)
3. [ ] Cho phép truy cập microphone
4. [ ] Quan sát avatar xuất hiện? ⬜ Yes / ⬜ No
5. [ ] Avatar nói tiếng Việt? ⬜ Yes / ⬜ No
6. [ ] Lip-sync khớp với giọng nói? ⬜ Yes / ⬜ No
7. [ ] Nói "Xin chào" → transcript hiện ra? ⬜ Yes / ⬜ No
8. [ ] Trả lời câu hỏi → được chấm điểm? ⬜ Yes / ⬜ No

### Scenario 2: Report & Scoring
1. [ ] Sau khi finish interview
2. [ ] Vào báo cáo kết quả
3. [ ] Xem 5 tiêu chí scoring:
   - [ ] Chuyên môn (chuyenMon)? ⬜ 0-100
   - [ ] Dự án (duAn)? ⬜ 0-100
   - [ ] Tư duy (tuDuy)? ⬜ 0-100
   - [ ] Truyền đạt (truyenDat)? ⬜ 0-100
   - [ ] Mức khớp (mucKhop)? ⬜ 0-100
4. [ ] Overall score được tính trung bình? ⬜ Yes / ⬜ No

---

## ⚠️ Troubleshooting Guide

### ❌ GPU không detect
**Triệu chứng**: `nvidia-smi` fails in Cell 1  
**Nguyên nhân**: Runtime chưa hỗ trợ GPU

**Giải pháp**:
```bash
Runtime → Factory reset runtime → Select GPU runtime type
```
Hoặc chọn "High RAM" runtime thay vì "Free" tier

### ❌ Docker container crash
**Triệu chứng**: Container exits immediately  
**Nguyên nhân**: GPU không sẵn sàng hoặc port conflict

**Giải pháp**:
```python
# Check logs
!docker logs hiremind-ai

# Kill and rebuild
!docker rm -f hiremind-ai
!docker rmi hiremind-ai-service
# Re-run Cells 3 & 4
```

### ❌ Ngrok tunnel không connect
**Triệu chứng**: "Invalid auth token" or "No route found"  
**Nguyên nhân**: Token sai hoặc port mapping wrong

**Giải pháp**:
- [ ] Verify token: Check lại dashboard.ngrok.com
- [ ] Re-paste token trong Cell 5
- [ ] Ensure ports mapped correctly (8020 for API, 8010 for WebRTC)

### ❌ Service slow hoặc timeout
**Triệu chứng**: Response > 30 seconds  
**Nguyên nhân**: RAM/CPU overload trên Colab Free

**Giảm load**:
```python
# Use smaller STT model
AI_STT_MODEL=base  # thay vì small (nhanh hơn nhưng kém chính xác hơn)
AI_STT_MODEL=tiny  # nhanh nhất nhưng kém chính xác nhất
```
Edit trong Dockerfile hoặc .env before building

### ❌ Frontend không gọi được AI service
**Triệu chứng**: CORS error or network failure in browser  
**Nguyên nhân**: 
- CORS not allowed (check AI_ALLOWED_ORIGIN)
- ngrok URL changed (must re-copy after restart)

**Giải pháp**:
```bash
# Check current ngrok URLs
!ngrok http 8020
```

---

## 💰 Cost Monitoring

### Free Tier Limits
| Resource | Limit | Your Usage | Status |
|----------|-------|------------|--------|
| GPU Hours | ~12h/session | TBD | Monitor via Runtime info |
| Storage | Ephemeral | Lost on disconnect | No permanent storage |
| Bandwidth | 60GB/month (ngrok free) | TBD | Check dashboard.ngrok.com |

**Action Items**:
- [ ] Set timer提醒自己 session hết hạn (~12h)
- [ ] Bookmark session URL để reconnect
- [ ] Export any critical data trước khi session end

---

## 🔄 Migration Path (Khi cần production)

### Trigger Points:
- [ ] User > 10 concurrent interviews/hour
- [ ] Need > 12h continuous uptime
- [ ] Require dedicated GPU SLA

### Next Steps:
1. **Phase 1**: Rent GCP Spot Instance (Google Cloud)
   - Machine: n1-standard-2 + T4 GPU
   - Cost: ~$0.15/hr = $110/month
   
2. **Phase 2**: Own domain + Load balancing
   - Replace ngrok with dedicated domain
   - SSL certificate management
   
3. **Phase 3**: Auto-scaling cluster
   - Multiple GPU instances
   - Kubernetes orchestration

---

## 📝 Documentation & Handoff

### Files Created:
- [ ] `/docs/colab-deployment-guide.md` - Complete deployment manual
- [ ] `/docs/colab-setup-notebook.ipynb` - Ready-to-use Colab notebook
- [ ] This checklist document

### Important Links:
- **Colab Notebook Template**: D:\AI-Interview-Platform-BE\docs\colab-setup-notebook.ipynb
- **Frontend App**: https://aiinterview-vn.vercel.app
- **Backend API**: https://aiinterview-vn.onrender.com/api/v1
- **AI Service (ngrok)**: [Insert ngrok URL from Step 5]
- **ngrok Dashboard**: https://dashboard.ngrok.com
- **Colab Session**: [Insert Colab session URL after creation]

### Contact/Support:
- Spec documentation: `/specs/012-avatar-interview-api/spec.md`
- AI Service code: https://github.com/PNV-do-an/AI-Interview-Platform-AI
- Hardware requirements: `/memory/hardware-gtx1650-4gb.md`

---

## ✅ Final Verification Checklist

Before considering deployment successful:

- [ ] All 6 Colab cells run without errors
- [ ] ngrok tunnel established successfully
- [ ] Health check passes at ngrok public URL
- [ ] At least 3 out of 6 endpoint tests pass
- [ ] Frontend can call AI service (no CORS/network errors)
- [ ] Can start avatar interview session
- [ ] Audio/video streaming works (microphone input)
- [ ] Speech-to-text transcription working
- [ ] Rubric scoring generates valid scores (0-100)
- [ ] Session can be completed and report viewed
- [ ] Documentation handed off to team/stakeholders

**Deployment Date**: _______________  
**Deployed By**: _______________  
**Status**: ⬜ Successful / ⬜ Failed / ⬜ Needs Revision  

**Notes**:
```
```

---

**Created**: 2026-11-03  
**Last Updated**: 2026-11-03  
**Version**: 1.0  
**Tested On**: Google Colab Free Tier + GTX T4 GPU
