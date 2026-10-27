# AI Service với LiveTalking - Dockerfile cho HireMind AI Interview Platform
# Build: docker build -t hiremind-ai-service .
# Run:  docker run -p 8010:8010 -p 8020:8020 --gpus all hiremind-ai-service

FROM python:3.11-slim

# Cài dependencies hệ thống
RUN apt-get update && apt-get install -y \
    ffmpeg \
    libsm6 \
    libxext6 \
    git \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy Python dependencies và cài đặt
COPY requirements-ai.txt .
RUN pip install --no-cache-dir -r requirements-ai.txt

# Clone vendored LiveTalking (có weights rồi nên không cần tải lại)
RUN if [ ! -d "vendored/LiveTalking" ]; then \
        git clone https://github.com/Doubiiu/LiveTalking.git vendored/LiveTalking; \
    fi || true

COPY vendored/ ./vendored/
COPY app/ ./app/
COPY scripts/ ./scripts/
COPY config/ ./config/
COPY run_ai_server.py .

# Cài thêm deps của LiveTalking
RUN cd vendored/LiveTalking && pip install --no-cache-dir -r requirements.txt

# Copy bootstrap script (chạy để download weights trước khi build)
COPY scripts/bootstrap_weights.sh /app/
RUN chmod +x /app/scripts/bootstrap_weights.sh && \
    /app/scripts/bootstrap_weights.sh || echo "Weights may already be present"

# Tạo directories
RUN mkdir -p data/avatars data/record downloads models/wav2lip

# Copy assets (avatar images, configs)
COPY assets ./assets/
COPY config ./config/

EXPOSE 8010 8020

CMD ["uvicorn", "main:create_app", "--host", "0.0.0.0", "--port", "8020"]
