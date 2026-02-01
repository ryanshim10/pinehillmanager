#!/bin/bash

# Personal Plex 테스트 스크립트
# 사용법: ./scripts/test.sh

echo "🧪 Personal Plex 테스트 시작"
echo ""

TEST_PASSED=0
TEST_FAILED=0

# 색상 정의
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 테스트 함수
run_test() {
    local name=$1
    local command=$2
    
    echo -n "테스트: $name ... "
    if eval "$command" > /dev/null 2>&1; then
        echo -e "${GREEN}✅ PASS${NC}"
        ((TEST_PASSED++))
    else
        echo -e "${RED}❌ FAIL${NC}"
        ((TEST_FAILED++))
    fi
}

echo "1️⃣ Docker 컨테이너 상태 확인"
echo "─────────────────────────────"
run_test "Ollama 컨테이너 실행 중" "docker ps | grep -q personal-plex-ollama"
run_test "Open WebUI 컨테이너 실행 중" "docker ps | grep -q personal-plex-webui"
run_test "Pinehill Bridge 컨테이너 실행 중" "docker ps | grep -q personal-plex-bridge"

echo ""
echo "2️⃣ API 엔드포인트 테스트"
echo "─────────────────────────────"
run_test "Ollama API 응답" "curl -s http://localhost:11434/api/tags | grep -q 'models'"
run_test "Open WebUI 접속" "curl -s -o /dev/null -w '%{http_code}' http://localhost:3000 | grep -q '200\\|307'"
run_test "Pinehill Bridge Health" "curl -s http://localhost:8001/health | grep -q 'ok'"

echo ""
echo "3️⃣ AI 모델 테스트"
echo "─────────────────────────────"
run_test "Llama 3.1 모델 존재" "curl -s http://localhost:11434/api/tags | grep -q 'llama3.1'"

echo ""
echo "4️⃣ Pinehill Bridge 기능 테스트"
echo "─────────────────────────────"
run_test "Units API" "curl -s http://localhost:8001/api/units | grep -q 'unitId'"
run_test "Summary API" "curl -s http://localhost:8001/api/summary/2026-01 | grep -q 'month'"

echo ""
echo "═══════════════════════════════"
echo "📊 테스트 결과"
echo "═══════════════════════════════"
echo -e "통과: ${GREEN}$TEST_PASSED${NC}"
echo -e "실패: ${RED}$TEST_FAILED${NC}"
echo ""

if [ $TEST_FAILED -eq 0 ]; then
    echo -e "${GREEN}🎉 모든 테스트 통과! Personal Plex가 정상 작동 중입니다.${NC}"
    echo ""
    echo "💡 다음 단계:"
    echo "  1. 브라우저에서 http://localhost:3000 열기"
    echo "  2. 새 채팅 시작"
    echo "  3. '안녕하세요' 입력해서 AI 응답 확인"
    exit 0
else
    echo -e "${RED}⚠️ 일부 테스트가 실패했습니다.${NC}"
    echo ""
    echo "🔧 문제 해결:"
    echo "  1. docker-compose logs 명령으로 로그 확인"
    echo "  2. docker-compose restart로 서비스 재시작"
    echo "  3. install.sh를 다시 실행"
    exit 1
fi
