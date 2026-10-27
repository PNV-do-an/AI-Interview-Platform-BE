# Hướng dẫn Deploy Python AI Service (LiveTalking + FastAPI)

## Overview

Service này cung cấp avatar AI nói tiếng Việt thật qua WebRTC, kết hợp với FastAPI orchestration layer.

**Components:**
- **LiveTalking** (:8010): Avatar video engine với wav2lip lip-sync
- **FastAPI Wrapper** (:8020): API gateway cho FE, xử lý LLM/question/STT

## Prerequisites

### Hardware Requirements

| Component | GPU VRAM | RAM | Notes |
|---|---|---|---|
| LiveTalking | ≥ 4GB | 8GB | GTX 1650 trở lên |
| FastAPI | None | 2GB | CPU-only |
| **Total** | **≥ 4GB GPU** | **≥ 10GB** | Dùng chung VM nếu < 16GB VRAM |

### Software Stack

```yaml
OS: Ubuntu 22.04 LTS
GPU Driver: NVIDIA 516+ (CUDA 11.x)
Docker: 24.0+ with NVIDIA container toolkit
Python: 3.11 (venv trong container)
```

---

## Option A: Google Colab (FREE - Best for Testing)

**Ưu điểm:** Miễn phí, không cần setup, GPU T4 mạnh hơn yêu cầu  
**Nhược điểm:** Giới hạn 12 giờ/session, cần chạy thường xuyên

### 1. Mở Colab Notebook

Truy cập: https://colab.research.google.com/  
Hoặc tạo file mới từ GitHub repo của bạn.

### 2. Cài đặt dependencies

```python
# Install Docker và NVIDIA toolkit!pip install --quiet nvidia-docker2
!sudo apt-get update && sudo apt-get install -y docker.io !curl -fsSL https://get.docker.com | sh

# Configure Docker để dùng GPUimport subprocess;subprocess.run(['sudo','usermod','-aG','docker','ubuntu'])

# Restart Docker with GPU support!sed -i 's/"exec":"json"/"exec":"json","runtimes":{"nvidia":{"path":"nvidia-container-runtime","arguments":[]}}/' /etc/docker/daemon.json!sudo systemctl restart docker

# Verify GPU access!nvidia-smi !docker run --rm --gpus all nvidia/cuda:11.0-base nvidia-smi
```

### 3. Clone repo và build image

```python
# Clone repository!git clone https://github.com/PNV-do-an/AI-Interview-Platform-AI.git %21cd AI-Interview-Platform-AI

# Build Docker image (dự kiến 10-15 phút)
!docker build -t hiremind-ai-service .

# Verify image built successfully
!docker images hiremind-ai-service
```

### 4. Run container

```python
# Chạy container với port mapping!docker run -d \
--name hiremind-ai \
--gpus all \
-p 8010:8010 \
-p 8020:8020 \
-e AI_AVATAR_SERVER=http://livetalking:8010 \
-e AI_ALLOWED_ORIGIN=* \
hiremind-ai-service

# Kiểm tra logs!docker logs -f hiremind-ai &
```

### 5. Forward ports qua ngrok (Để expose ra internet)

```python
# Install ngrok (miễn phí cho testing)!pip install pyngrok

from pyngrok import ngrok

# Create HTTPS tunnel to AI service public URL = ngrok.connect(8020) print(f"✅ AI Service accessible at: {public_url}") # Optional: Also expose avatar interface ngrok_avatars = ngrok.connect(8010) print(f"🎭 Avatar interface at: {ngrok_avatar.public_url}")
```

Lưu `public_url` → Đây là URL để config vào Frontend.

### 6. Cấu hình Frontend

Trong Vercel Dashboard → Settings → Environment Variables:

```
REACT_APP_AI_SERVICE_URL=<public_url_từ_ngrok>
```

Restart deployment để áp dụng.

### 7. Sử dụng khi session hết hạn

Colab tự động disconnect sau ~12 giờ. Để giữ session hoạt động:
- Mở thêm notebook khác "ping" định kỳ mỗi 10 phút
- Hoặc dùng script automate reconnect

---

## Option B: Google Cloud Vertex AI (Production Ready)

### 1. Tạo Custom Instance Template

```bash
gcloud compute instances create hiremind-ai \
  --machine-type=n1-standard-2 \
  --accelerator=count=1,type=nvidia-tesla-t4 \
  --image-family=ubuntu-2204-lts \
  --image-project=ubuntu-os-cloud \
  --boot-disk-size=100GB \
  --boot-disk-type=pd-ssd \
  --zone=us-central1-a
```

### 2. SSH vào instance

```bash
gcloud compute ssh hiremind-ai --zone=us-central1-a
```

### 3. Install dependencies

```bash
# Update system
sudo apt update && sudo apt upgrade -y

# Install Docker
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER

# Install NVIDIA Container Toolkit
wget -P /tmp https://nvidia.github.io/libnvidia-container/gpgkey
sudo gpg --dearmor -o /usr/share/keyrings/nvidia-container-toolkit.gpg /tmp/gpgkey
curl -s -L https://nvidia.github.io/libnvidia-container/stable/docker/nvidia-container-toolkit.list | \
  sudo tee /etc/apt/sources.list.d/nvidia-container-toolkit.list
sudo apt update
sudo apt install nvidia-container-toolkit -y

# Restart Docker with NVIDIA support
sudo systemctl restart docker
sudo docker info | grep -i nvidia
```

### 4. Clone và build Docker image

```bash
cd ~
git clone https://github.com/PNV-do-an/AI-Interview-Platform-AI.git
cd AI-Interview-Platform-AI

# Build image (sẽ mất 10-15 phút)
docker build -t hiremind-ai-service .

# Check image size (should be ~5GB)
docker images hiremind-ai-service
```

### 5. Run container

```bash
docker run -d \
  --name hiremind-ai \
  --gpus all \
  -p 8010:8010 \
  -p 8020:8020 \
  -e AI_AVATAR_SERVER=http://livetalking:8010 \
  -e AI_ALLOWED_ORIGIN=https://your-vercel-app.vercel.app \
  hiremind-ai-service
```

### 6. Configure firewall

```bash
# Allow ports 80, 443, 8010, 8020
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw allow 8010/tcp
sudo ufw allow 8020/tcp
sudo ufw enable
```

### 7. Verify service is running

```bash
# Check container logs
docker logs -f hiremind-ai

# Test health endpoint
curl http://localhost:8020/health
# Expected response: {"status":"ok","avatarServer":"http://livetalking:8010",...}

# Check avatar status
curl http://localhost:8010/api/admin/sessions
# Expected response: {"code":0,"msg":"ok","data":{"sessions":[]}}
```

---

## Option B: Self-hosted Local Server

### 1. Install Docker Desktop for Windows

Download từ: https://www.docker.com/products/docker-desktop/

Enable WSL 2 backend and NVIDIA GPU passthrough.

### 2. Build and run

```powershell
cd D:\AI-Interview-Platform-AI

# Build image
docker build -t hiremind-ai-service .

# Run container (map to localhost ports)
docker run -d `
  --name hiremind-ai `
  --gpus all `
  -p 8010:8010 `
  -p 8020:8020 `
  -e AI_ALLOWED_ORIGIN=http://localhost:3000 `
  hiremind-ai-service
```

---

## Frontend Configuration

Sau khi AI service đã chạy, update `.env.production` của FE:

```env
REACT_APP_API_URL=https://your-render-backend.onrender.com/api/v1
REACT_APP_AI_SERVICE_URL=https://your-ai-server.hiremind.com
```

Thay thế bằng domain/IP thực tế của bạn.

---

## Troubleshooting

### Error: No GPU detected

```bash
# Check if Docker can see GPU
docker run --rm --gpus all nvidia/cuda:11.0-base nvidia-smi

# If this fails, reinstall NVIDIA Container Toolkit
sudo apt install --reinstall nvidia-container-toolkit
sudo systemctl restart docker
```

### Error: Port already in use

```bash
# Check what's using port 8010/8020
netstat -ano | findstr ":8010"
netstat -ano | findstr ":8020"

# Stop conflicting processes or change ports in .env
```

### Error: OOM (Out of Memory)

LiveTalking + STT nặng RAM. Nếu gặp OOM:
- Giảm batch_size xuống 8 (edit config/livetalking.yaml)
- Dùng model nhỏ hơn: `AI_STT_MODEL=base` hoặc `tiny`

---

## Monitoring

```bash
# Real-time GPU usage
watch -n 1 nvidia-smi

# Container logs
docker logs -f hiremind-ai

# Resource usage
docker stats hiremind-ai
```

---

## Cost Estimation

### Option A: Google Colab (FREE)
| Resource | Cost | Notes |
|---|---|---|
| GPU T4 | $0/hr | Miễn phí, giới hạn 12h/session |
| Storage | $0 | ephemeral, mất khi disconnect |
| **Total** | **$0/month** | Ideal cho testing/demo |

### Option B: Google Cloud (Production)
| Instance Type | Hourly Rate | Monthly Cost |
|---|---|---|
| n1-standard-2 + T4 | ~$0.50/hr | ~$360/month (24/7) |
| Spot/TCP instance | ~$0.15/hr | ~$110/month (24/7) |

Giảm chi phí bằng cách:
- Chỉ chạy khi cần test (Colab preferred)
- Tự động stop/start theo schedule
- Dùng spot instances cho non-critical workloads

## Migration Path

1. **Phase 1 - Testing**: Dùng Google Colab (miễn phí) để validate functionality
2. **Phase 2 - Beta**: Chuyển sang GCP Spot Instance khi có user thật (~$110/tháng)
3. **Phase 3 - Production**: Scale up với auto-scaling groups khi cần
