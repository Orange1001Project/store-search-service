# store-search-service

온누리상품권 가맹점 검색 서비스(PoC) 개발 레포.
검색 모델 학습·평가와 데이터 가공은 [AI 레포(store-search)](https://github.com/Orange1001Project/store-search)에서 하고, 이 레포는 **서비스를 만드는 코드와 개발 기준 문서**를 둔다.

## 처음 왔다면

1. [`docs/dev/LOCAL_DEV_SETUP.md`](docs/dev/LOCAL_DEV_SETUP.md)대로 Docker를 설치하고 DB를 띄운다.
2. [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)로 전체 구성을 본다.
3. 맡은 도메인의 요구사항([`docs/product/REQUIREMENTS.md`](docs/product/REQUIREMENTS.md))과 API 명세([`docs/api/openapi.yaml`](docs/api/openapi.yaml))를 본다.

## 폴더

| 폴더 | 내용 |
|---|---|
| `docs/product/` | 요구사항, 서비스 정책 |
| `docs/ARCHITECTURE.md` | 구성과 그렇게 정한 이유 |
| `docs/db/` | 표 설명(`DB_TABLES.md`), 설계 이유(`DB_DESIGN_WHY.md`), `schema.sql` |
| `docs/api/` | API 공통 규칙, OpenAPI 3.0.3 명세, 명세 검사 규칙(`.spectral.yaml`) |
| `docs/design/` | 화면 설계(그림 41장), 화면별 API 대응표(`SCREEN_API_MAP.md`) |
| `docs/search/` | 검색 인계(AI 평가 → 서비스로 옮겨야 하는 동작) |
| `docs/dev/` | 로컬 개발 준비 |
| `infra/` | 로컬 Docker Compose(DB: PostgreSQL 16 + PostGIS + pgvector) |
| `seed/` | 서비스가 쓰는 작은 기준 데이터(업종, 업종 매핑, 브랜드 별칭, 검색 정책 값, 관련 확률 계수) |

코드 폴더(API 서버, 화면, 임베딩 서버)는 개발을 시작하면서 더한다.

## 지키기로 한 것

- **git에 올리지 않는 것**: `infra/.env`(비밀번호), 원천 엑셀(사업자번호 포함), 임베딩 모델 파일, DB 덤프. 큰 파일은 Drive에 둔다.
- **기준 문서는 한 곳에만**: 요구사항·정책·DB·API 문서는 이 레포가 기준이다. AI 레포에 남은 같은 이름의 문서는 옮기기 전 기록이다. 문서에 버전 번호·변경 이력은 적지 않고 git 기록으로 본다.
- **모두가 쓰는 파일**(`docs/api/openapi.yaml`, `docs/db/schema.sql`)을 바꿀 때는 PR에 바뀐 점을 적고 관련 도메인 담당이 확인한다.
- 문서에 나오는 `PR #9`~`#12`, `src/`·`scripts/`·`results/`·`data/`·`colab/` 경로는 AI 레포 기준이다.

## 출처

AI 레포 `store-search` main(2026-11-18, PR #13 머지 `0e2f07b`)에서 옮겼다. 거기에 닫힌 PR #12의 아키텍처 문서, 로컬 개발 준비, "PoC는 Redis를 쓰지 않음"(갱신 토큰은 DB 표, 검색 결과 캐시는 API 서버 메모리)을 더했다. 이 셋은 AI 레포 main에는 없고 이 레포에만 있다.
