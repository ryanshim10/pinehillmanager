# 원룸(19세대) 관리 앱 – 1차 설계 (MVP)

## 목표
- Android 앱
- 계약서 촬영 → OCR → 세입자/계약 필드 자동 추출 + 원본 이미지 저장
- 카카오뱅크 입금 SMS 파싱 → 입금 기록 자동 생성/매칭
- 19세대 현황(임대중/정비중/소송 등) 관리
- **공유 기능**: ryan(관리자) + 누나(현황 공유)

---

## 권한/공유(추천 방향)
### 옵션 A (가장 쉬움 / 배포 용이)
- **누나는 ‘뷰어(Viewer)’로만** 사용
- ryan 폰에서 “월별 리포트 PDF/이미지” 내보내기 → 카톡/메일로 전송
- 장점: 서버/로그인/동기화 없이 매우 단순
- 단점: 실시간이 아님

### 옵션 B (실시간 공유, 최소 서버)
- ryan 폰이 **로컬 DB**를 유지
- 주기적으로 “현황 스냅샷(유닛 상태+미납 요약)”을 **클라우드(예: Firebase/Firestore 또는 간단 API 서버)** 로 업로드
- 누나는 클라우드 스냅샷을 읽기만 함(읽기 전용)
- 장점: 실시간/간편
- 단점: 서버/클라우드 비용/계정관리 필요

> SMS 읽기/계약서 원본 등 민감 데이터는 ‘공유 대상 제외’하고, 공유는 **현황/요약만** 하는 걸 강력 추천.

---

## 데이터 모델(초안)
### Unit (세대)
- unitId: string (예: PINE-201)
- roomNo: int (예: 201)
- floor: int (예: 2)
- status: enum (임대중|정비중|소송|공실|기타)
- roomType: string? (예: 1.5룸, 투룸)
- size: string? (면적/평형 등 자유)
- targetPrice: string? (예: 500-50, 500-60)
- createdAt, updatedAt

### Tenant (세입자)
- tenantId
- phone
- name? (선택)
- unitId(FK)

### Contract
- contractId
- unitId(FK)
- tenantId(FK)
- deposit
- monthlyRent
- startDate
- endDate
- moveInDate
- moveOutDate
- moveOutFeeText
- contractImagePath (원본)
- createdAt, updatedAt

### Payment
- paymentId
- unitId(FK)
- tenantId(FK, optional)
- paidAt
- amount
- source: enum(SMS|MANUAL)
- rawSmsHash/preview (민감정보 최소 저장)
- matched: bool

---

## 유닛 샘플(ryan 제공)
- PINE-201 201 2 임대중 (1.5룸, 500-50)
- PINE-202 202 2 임대중
- PINE-203 203 2 임대중
- PINE-204 204 2 소송
- PINE-205 205 2 임대중 (투룸, 500-60)
- PINE-206 206 2 임대중 (투룸, 500-60)
- PINE-207 207 2 임대중
- PINE-301 301 3 임대중 (1.5룸, 500-50)
- PINE-302 302 3 임대중
- PINE-303 303 3 임대중
- PINE-304 304 3 임대중
- PINE-305 305 3 임대중 (투룸, 500-60)
- PINE-306 306 3 임대중 (투룸, 500-60)
- PINE-307 307 3 임대중
- PINE-401 401 4 임대중 (1.5룸, 500-50)
- PINE-402 402 4 임대중
- PINE-403 403 4 임대중
- PINE-404 404 4 임대중
- PINE-405 405 4 정비중

---

## 다음에 필요한 것
1) 누나 공유는 옵션 A/B 중 뭐로 갈지 결정
2) SMS 예시(개인정보 마스킹) 1~2개
3) 계약서 양식 예시(민감정보 가리고) 1장
