#!/bin/bash

# Personal Plex 설치 스크립트
# 사용법: ./install.sh

set -e

echo "🚀 Personal Plex 설치 시작..."
echo ""

# 1. Docker 설치 확인
if ! command -v docker &> /dev/null; then
    echo "❌ Docker가 설치되어 있지 않습니다. 설치를 진행합니다..."
    curl -fsSL https://get.docker.com | sh
    sudo usermod -aG docker $USER
    echo "⚠️ Docker 설치 완료. 터미널을 재시작하거나 'newgrp docker'를 실행해주세요."
    exit 1
fi

if ! command -v docker-compose &> /dev/null; then
    echo "📦 Docker Compose 설치 중..."
    sudo curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
    sudo chmod +x /usr/local/bin/docker-compose
fi

echo "✅ Docker 확인 완료"
echo ""

# 2. 환경 설정
if [ ! -f .env ]; then
    echo "⚙️ .env 파일 생성 중..."
    cp .env.example .env
    echo "✅ .env 파일이 생성되었습니다. 필요시 수정해주세요."
fi

# 3. 데이터 디렉토리 생성
echo "📁 데이터 디렉토리 생성 중..."
mkdir -p data/ollama data/open-webui

# 4. Docker 이미지 빌드 및 실행
echo "🐳 Docker 컨테이너 빌드 및 실행 중..."
docker-compose build
docker-compose up -d

echo ""
echo "⏳ 서비스 시작 대기 중... (약 30초)"
sleep 30

# 5. Ollama 모델 다운로드
echo "🤖 AI 모델 다운로드 중... (시간이 소요됩니다)"
docker exec personal-plex-ollama ollama pull llama3.1:8b

echo ""
echo "✅ 설치 완료!"
echo ""
echo "📱 접속 주소:"
echo "  - Open WebUI (AI 채팅): http://localhost:3000"
echo "  - Ollama API: http://localhost:11434"
echo "  - Pinehill Bridge: http://localhost:8001"
echo ""
echo "🧪 테스트 실행: ./scripts/test.sh"
