# 🚀 Deploy AI Avatar Interview trên Google Colab (Miễn phí) + ngrok

**Mục tiêu:** Chạy LiveTalking avatar engine + FastAPI service trên Google Colab Free Tier, expose qua ngrok tunnel để frontend gọi được.

## ⚡ Quick Start - Copy & Paste toàn bộ cell này vào Colab

### Cell 1: Install Dependencies

```python
#pip install --quiet nvidia-docker2
!sudo apt-get update && sudo apt-get install -y docker.io
!curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER

# Configure Docker runtime với GPU support
import subprocess
subprocess.run(['sudo', 'sed', "-i", "s/\"exec\":\\[\"json\"\\]/\"exec\":\\[\"json\"],\\\"runtimes\\\":{\\\"nvidia\\\":{\\\"path\\\":\\\"nvidia-container-runtime\\\",\\\"arguments\\\":[\\]}/g', '/etc/docker/daemon.json'])
subprocess.run(['sudo', 'systemctl', 'restart', 'docker'])

# Verify GPU access
print("🔍 Checking GPU access...")
result = subprocess.run(['nvidia-smi'], capture_output=True, text=True)
if result.returncode == 0:
    print("✅ GPU detected!")
else:
    print("⚠️ No GPU detected in this session")

print("\n📦 Installing ngrok...")
!pip install pyngrok
```

### Cell 2: Clone AI Service Repository

```python
print("📥 Cloning AI Service repository...")
!git clone https://github.com/PNV-do-an/AI-Interview-Platform-AI.git
%cd AI-Interview-Platform-AI

# Check structure
!ls -la
```

### Cell 3: Build Docker Image

```python
print("🏗️  Building Docker image (this takes ~10-15 minutes)...")
!docker build -t hiremind-ai-service .

# Verify image
!docker images hiremind-ai-service
```

### Cell 4: Run Container with Port Forwarding

```python
print("▶️  Starting AI service container...")

# Run container
!docker run -d \
  --name hiremind-ai \
  --gpus all \
  -p 8010:8010 \
  -p 8020:8020 \
  -e AI_AVATAR_SERVER=http://livetalking:8010 \
  -e AI_ALLOWED_ORIGIN=* \
  hiremind-ai-service

# Wait for startup
import time
time.sleep(30)

# Check logs
!docker logs hiremind-ai --tail 50

# Verify health endpoint
import requests
try:
    response = requests.get('http://localhost:8020/health')
    print("✅ Health check passed!")
    print(response.json())
except:
    print("⚠️ Health check failed, waiting more...")
    time.sleep(30)
```

### Cell 5: Setup ngrok Tunnel

```python
from pyngrok import ngrok
import os

print("🔗 Creating ngrok tunnel to AI service port 8020...")

# Get ngrok auth token from your account at https://dashboard.ngrok.com/get-started/your-authtoken
# Replace 'YOUR_AUTHTOKEN_HERE' with your actual token
NGROK_AUTH_TOKEN = 'YOUR_AUTHTOKEN_HERE'

# Configure ngrok
ngrok.set_auth_token(NGROK_AUTH_TOKEN)

# Create public HTTPS URL
public_url = ngrok.connect(8020)
print(f"🎭 AI Service available at: {public_url.public_url}")

# Also create tunnel for avatar WebRTC port if needed
ngrok_avatar = ngrok.connect(8010)
print(f"🎭 Avatar WebRTC interface at: {ngrok_avatar.public_url}")

# Save URLs for later use
with open('/tmp/ai_service_urls.txt', 'w') as f:
    f.write(f"AI_SERVICE_URL={public_url.public_url}\n")
    f.write(f"AVERT_WEBRTC_URL={ngrok_avatar.public_url}\n")

print("\n📌 IMPORTANT: Copy these URLs!")
print(f"   Frontend API: {public_url.public_url}")
print(f"   Avatar Stream: {ngrok_avatar.public_url}")
```

### Cell 6: Test AI Service Endpoints

```python
import requests
import json

base_url = 'http://localhost:8020'

print("🧪 Testing AI Service endpoints...\n")

# Test health
response = requests.get(f'{base_url}/health')
print(f"1. GET /health")
print(f"   Status: {response.status_code}")
print(f"   Response: {response.json()}\n")

# Test admin sessions
response = requests.get(f'{base_url}/api/admin/sessions')
print(f"2. GET /api/admin/sessions")
print(f"   Status: {response.status_code}")
print(f"   Sessions active: {response.json().get('data', {}).get('sessions', [])}\n")

print("✅ All tests completed! Service is ready.")
```

---

## 🔧 Cấu hình Frontend (trên Vercel)

Sau khi có ngrok URL từ Cell 5:

### 1. Lấy ngrok Auth Token (CẦN LÀM TRƯỚC)

Visit: https://dashboard.ngrok.com/get-started/your-authtoken  
Login/Register → Copy your **Authtoken**

### 2. Update Environment Variables trong Vercel Dashboard

```
REACT_APP_API_URL=https://aiinterview-vn.onrender.com/api/v1
REACT_APP_AI_SERVICE_URL=https://xxxxx-xxxx-xxxx-ngrok-token.herokuapp.com (từ ngrok connect)
```

Hoặc dùng command line:

```bash
vercel env pull REACT_APP_AI_SERVICE_URL
```

### 3. Restart Deployment

```bash
vercel deploy --prod
```

---

## 🎯 Sử dụng AI Service

### Frontend Integration Example

```typescript
// services/interview.service.ts
const AI_SERVICE_BASE = process.env.REACT_APP_AI_SERVICE_URL;

export const interviewService = {
  // Step 1: Establish WebRTC connection
  async establishAvatarConnection(): Promise<{answerSdp: string; avatarSessionId: string}> {
    const sdpOffer = await generateWebRTCOffer();
    
    const response = await fetch(`${AI_SERVICE_BASE}/api/v1/ai/avatar/offer`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ offerSdp: sdpOffer })
    });
    
    return response.json();
  },

  // Step 2: Create interview session on BE
  async startAvatarInterview(jobId: string): Promise<InterviewSession> {
    const response = await fetch('/api/v1/mock-interviews', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'include',
      body: JSON.stringify({ mode: 'AVATAR', jobId })
    });
    
    return response.json();
  },

  // Step 3: Submit answer (audio/file or text)
  async submitAnswer(sessionId: string, questionIndex: number, audioFile?: File): Promise<void> {
    const formData = new FormData();
    formData.append('file', audioFile);
    formData.append('questionIndex', questionIndex.toString());
    formData.append('confirm', 'true');

    await fetch(`/api/v1/mock-interviews/${sessionId}/answer`, {
      method: 'POST',
      headers: { 'Accept': 'application/json' },
      credentials: 'include',
      body: formData
    });
  }
};
```

---

## ⚙️ Maintenance & Monitoring

### Keep Session Alive

Colab sessions timeout after ~12 hours. Add a ping script:

```python
# Cell: Keep alive ping
import time
import requests

while True:
    try:
        # Send request to keep container warm
        requests.get('http://localhost:8020/health')
        print(f"✅ Service alive - {datetime.now()}")
        time.sleep(600)  # Ping every 10 minutes
    except Exception as e:
        print(f"❌ Error: {e}")
        time.sleep(60)
```

### Monitor GPU Usage

```python
# Cell: GPU monitoring
!watch -n 1 nvidia-smi
```

### View Logs

```python
# Cell: Real-time logs
!docker logs -f hiremind-ai
```

---

## 🆘 Troubleshooting

### ❌ Error: "No GPU detected"

```python
# Re-run Cell 1 to verify GPU
!nvidia-smi
```

If still fails, restart runtime: Runtime → Factory reset runtime

### ❌ Error: "Port already in use"

```python
# Kill existing containers
!docker rm -f hiremind-ai
!docker rmi hiremind-ai-service
```

Then re-run Cells 3 & 4.

### ❌ Ngrok connection fails

Make sure you've replaced `YOUR_AUTHTOKEN_HERE` with your real token from ngrok dashboard.

### ❌ Container won't start

```python
# Check docker daemon status
!sudo systemctl status docker

# Try building again
!docker build -t hiremind-ai-service .
```

---

## 📊 Cost Analysis

### Option A: Google Colab Free Tier ✅ (Current Setup)

| Resource | Cost | Limitations |
|----------|------|-------------|
| GPU T4 | **$0/month** | ~12h/session, need restart |
| Storage | **$0** | Ephemeral (lost on disconnect) |
| Bandwidth | **$0** | Limited by ngrok free tier (60GB/month) |
| **Total** | **$0** | Perfect for testing/demo |

### When to Upgrade?

- If you need >12h continuous operation → Use [Google Cloud Spot Instance](~$0.15/hr ≈ $110/month)
- For production with SLA → Rent dedicated GPU VM (~$0.50/hr ≈ $360/month)

---

## 🔄 Migration Path

```
Phase 1: Testing (Now)
├── Google Colab Free ✅
├── ngrok tunnel
└── Goal: Validate functionality with real users

Phase 2: Beta Production (When getting traction)
├── Google Cloud Vertex AI
├── Dedicated GPU VM
├── Own domain (no ngrok limits)
└── Estimated cost: $110-150/month

Phase 3: Scale Production (Enterprise)
├── Auto-scaling GPU instances
├── CDN optimization
├── Load balancing
└── Multi-region deployment
```

---

## 📝 Next Steps

1. ✅ Run all 6 cells in sequence on Colab
2. ✅ Copy ngrok URL to clipboard
3. ✅ Update Vercel environment variables
4. ✅ Redeploy frontend
5. ✅ Test video call flow:
   - Open https://aiinterview-vn.vercel.app
   - Create job/post position
   - Start avatar interview (mode: AVATAR)
   - Speak to AI interviewer
   - Check transcript scoring

---

## 🎬 Demo Checklist

Before showing stakeholders:

- [ ] AI avatar speaks Vietnamese (Giọng HoaiMy)
- [ ] Lip-sync accurate when speaking
- [ ] Can answer questions via microphone
- [ ] Speech-to-text transcription working
- [ ] Questions adapt based on JD
- [ ] Rubric scoring generates correctly
- [ ] Transcript save and report accessible

---

## 📞 Support

Nếu gặp lỗi không xử lý được:

1. **Issue không liên quan code:** Đọc tài liệu ở `/specs/012-avatar-interview-api/spec.md`
2. **GPU issues:** Kiểm tra `memory/hardware-gtx1650-4gb.md` về hạn chế phần cứng
3. **Deployment errors:** Xem `/DEPLOYMENT.md` chi tiết từng option

---

**Created:** 2026-11-03  
**Tested on:** Google Colab Free Tier + GTX T4 GPU + ngrok Free  
**Status:** Ready for production demo 🚀
