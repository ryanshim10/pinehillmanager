# Pinehill Manager 1.0

19세대 원룸 관리용 안드로이드 앱

## 주요 기능
- 19세대(Units) 현황 관리
- 계약서 OCR 스캔 및 세입자 정보 추출
- 카카오뱅크 입금/출금 SMS 자동 파싱
- 월세/공용지출 관리
- 구글시트 연동 (요약 공유)

## 기술 스택
- Kotlin
- Jetpack Compose
- Room (SQLite)
- ML Kit OCR
- SMS Reader

## 프로젝트 구조
```
app/src/main/java/com/ryan/pinehill/
├── data/
│   ├── model/          # 데이터 모델 (Unit, Tenant, Payment, Expense)
│   ├── dao/            # Room DAO
│   └── repository/     # Repository 패턴
├── ui/
│   ├── screens/        # 화면 (Compose)
│   ├── components/     # 재사용 컴포넌트
│   └── viewmodel/      # ViewModel
└── util/               # 유틸리티 (SMS 파서 등)
```

## 빌드 방법
1. Android Studio 열기
2. `File → Open` → `pinehill-manager` 폴더 선택
3. Sync Project with Gradle Files
4. Run (에뮬레이터 또는 실기기)

## 초기 데이터
19세대 정보는 `UnitSeeder`에 하드코딩되어 있음.
