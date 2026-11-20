# 로컬 개발 준비

> 기준: [`ARCHITECTURE.md`](../ARCHITECTURE.md), [`V1__init.sql`](../../backend/src/main/resources/db/migration/V1__init.sql), [`api/openapi.yaml`](../api/openapi.yaml)
> 파일: [`infra/docker-compose.yml`](../../infra/docker-compose.yml), [`infra/db/Dockerfile`](../../infra/db/Dockerfile), [`infra/.env.example`](../../infra/.env.example)
> **2026-11-17 Windows 11 + Docker Desktop 29.9에서 DB를 띄워 확인**(PostgreSQL 16.16, PostGIS 3.7.0, pgvector 0.8.0, 표 20개, 업종 17·결제 2, DBeaver 접속). 막히는 곳은 8절에 더해 주세요.

## 0. 로컬에서 무엇이 어디서 도나

| 구성 요소 | 로컬에서 | 포트 | 상태 |
|---|---|---|---|
| DB(PostgreSQL + PostGIS + pgvector) | Docker(`db`) | 127.0.0.1:5432 | ✅ 있음 |
| 임베딩 서버 | Docker(`embedding`) | 127.0.0.1:8001 | ✅ 있음(5절) |
| API 서버(Spring Boot) | IDE에서 실행 | 8080 | 아직 없음 |
| 화면(React) | `npm run dev` | 5173 등 | 아직 없음 |
| Nginx | 로컬에선 안 씀(화면 개발 서버가 `/api`를 8080으로 넘김) | | |

## 1. 환경 세팅 전에 확인할 것

⛔는 막히면 진행이 안 되는 것이다.

| # | 확인할 것 | 왜 |
|---|---|---|
| ⛔1 | **PC 관리자 계정, 가상화 켜짐** | WSL2 설치에 필요. 설치 때 "변경 허용" 창에서 예를 누를 수 있으면 된다 |
| ⛔2 | **Docker Desktop 라이선스** | 직원 250명 이상 또는 매출 1천만 달러 이상인 회사는 유료. 해당하면 회사 확인. 대안은 WSL 안에 Docker 엔진만 설치 |
| ⛔3 | **회사 네트워크 프록시·보안 검사** | Docker 이미지·Gradle·npm 내려받기가 막히거나 인증서 오류가 날 수 있다(2026-11-17 PC에서는 문제없었음) |
| 4 | **원천 엑셀 보관 위치** | 11월 원천(약 22만 6천 행)에는 사업자번호가 들어 있다. **git에 올리지 않고** Drive에 둔다 |
| 5 | **첫 적재·벡터 22만 개 생성 방법** | CPU로는 몇 시간. 한 사람이 GPU(Colab)로 한 번 만들고 DB 덤프를 공유하면 나머지는 복원만(6절) |
| 6 | **평가 "정답 결과 파일"** | 자바 검색 이식 검증(NFR-QLT-01)용. AI 레포에서 질의별 결과·순위를 뽑아 넘긴다 |
| 7 | 네이버 지도 Client ID(서비스 URL에 `http://localhost:5173` 등록) | 화면 지도 |
| 8 | 카카오·구글 개발용 앱(콜백 `http://localhost:8080/api/v1/auth/{provider}/callback`) | P1 로그인. 나중에 해도 됨 |

## 2. 설치

### 2-1. 공통

| 도구 | 버전 | 확인 명령 |
|---|---|---|
| Git | 최신 | `git --version` |
| JDK | 21(Temurin 등) | `java -version` |
| Node.js | LTS(20 이상) | `node -v` |
| DB 도구 | DBeaver Community 등 | |

### 2-2. Docker Desktop (Windows)

1. docker.com에서 Docker Desktop for Windows(**AMD64**, 인텔·AMD PC 공통) 설치 파일을 받는다.
2. 설치 화면(Configuration): **Per-user installation** + **WSL 2** 선택, Add shortcut to desktop은 자유 → OK.
3. 실행했을 때 `WSL not installed`가 나오면 Quit → **관리자 PowerShell**에서 `wsl --install --no-distribution` → 재부팅 → 다시 실행.
4. 약관 Accept → 로그인·설문은 Skip 가능 → 왼쪽 아래 **Engine running**.
5. (권장) 설정 → General: "Start Docker Desktop when you sign in" 끄기, "Send usage statistics" 끄기.
6. 확인: `docker run --rm hello-world`

WSL 메모리는 기본이 PC 메모리의 절반(32GB PC면 약 16GB)이라 보통 그대로 둔다. 모자라면 `%UserProfile%\.wslconfig`에 아래를 두고 `wsl --shutdown`.

```ini
[wsl2]
memory=16GB
```

### 2-3. 줄바꿈

Windows에서 `Dockerfile`·`.sh`·`.sql`이 CRLF로 바뀌면 컨테이너에서 실패할 수 있다. 저장소의 `.gitattributes`(`* text=auto eol=lf`)가 텍스트 파일을 LF로 받게 한다.

## 3. 띄우기

```bash
cd infra
cp .env.example .env      # POSTGRES_PASSWORD에 아무 비밀번호나(로컬 전용)
docker compose up -d db
docker compose ps         # db가 healthy인지
cd ../backend
./gradlew bootRun         # 처음 뜰 때 Flyway가 표 20개와 업종·결제 기본 데이터를 만든다
```

- Docker는 빈 DB만 만든다. 표는 API 서버가 뜰 때 Flyway가 `db/migration`의 SQL로 만든다.
- API 서버는 `infra/.env`에서 DB 접속 값을 읽는다(환경 변수를 따로 넣지 않아도 됨).
- 처음엔 이미지·라이브러리를 받느라 몇 분 걸린다. 테스트(`./gradlew test`)는 같은 DB 이미지를 빈 컨테이너로 띄워 V1부터 적용해 본다(Docker가 켜져 있어야 함).

## 4. DB 다루기

| 하고 싶은 것 | 방법 |
|---|---|
| 접속 | DBeaver → 새 연결 → PostgreSQL, Host `localhost`, Port `5432`, Database `storesearch`, User `storesearch`, Password는 `infra/.env`의 값. 또는 `docker compose exec db psql -U storesearch -d storesearch` |
| 테이블 확인 | 우리 표 20개 + PostGIS가 만드는 `spatial_ref_sys`(로그 파티션은 따로 더 보임). `SELECT * FROM category;` → 17행 |
| 처음부터 다시 | `docker compose down -v`(데이터 삭제) → `docker compose up -d db` → API 서버 실행 |
| 테이블 구조 바꾸기 | 아래 "DB 구조 바꾸기" |
| 로그 파티션 | V1이 2026-11 ~ 2027-12를 만들어 둔다. 그 뒤는 스케줄러가 두 달 앞까지 만든다(스케줄러는 아직 없음) |

**DB 구조 바꾸기**

DB는 각자 PC에 따로 있고, 합치는 것은 DB가 아니라 git의 SQL 파일이다. 남이 바꾼 구조는 git으로 받아 API 서버를 실행하면 내 DB에도 들어간다.

| 시기 | 방법 |
|---|---|
| **첫 배포 전(지금)** | `V1__init.sql`을 직접 고친다. 받은 사람은 API 서버가 "checksum mismatch"로 멈추면 `docker compose down -v` 후 다시 띄운다 |
| **첫 배포 후** | V1은 고치지 않는다. 바꿀 것만 새 파일로 더한다(`V2__add_x.sql`, `V3__…`). 번호가 겹치면 나중에 머지하는 쪽이 번호를 올린다 |

어느 쪽이든 PR에 `DB_TABLES.md`(컬럼표·관계 그림)를 같이 고친다.

**기본 데이터 넣기**(스크립트는 아직 없음)

| 데이터 | 원본 | 넣을 표 |
|---|---|---|
| 업종 17개, 결제 2개 | V1(이미 들어감) | `category`, `payment_method` |
| 산업분류 → 업종 매핑 | `seed/category/ksic_onnuri_v1.csv` | 적재 때 사용 |
| 브랜드 별칭 | `seed/service/brand_alias_v1.csv` | `brand_alias` |
| 지명 사전 | AI 레포 `data/geo/gazetteer_v1.parquet`(Drive) | `gazetteer`(`dict_version` = `gazetteer_v1`) |
| 확장어 캐시 | AI 레포 `results/service_pipeline/x8/expansions_Qwen2.5-14B-Instruct.csv` | `expansion_cache` |
| 지명 제외 목록 | 정책·해석 테스트의 목록을 파일로 정리 필요 | `place_exclusion` |
| 첫 관리자 계정 | 배포 스크립트(비밀번호 해시, `must_change_password` = 참) | `admin_user` |

검색 정책 값(`seed/service/search_policy_v1.yaml`)과 관련 확률 계수(`seed/service/calibrator.json`)는 DB가 아니라 API 서버가 파일로 읽는다([인계 문서](../search/SEARCH_HANDOFF.md) 1절).

## 5. 임베딩 서버

`embedding-server/`(FastAPI, CPU). `POST /embed`(문장 최대 64개 → 1024차원 정규화 벡터 + 모델 버전), `GET /health`.

1. 모델 bge-m3 미세조정 v1(`bge_m3_ft_mj_20261014_0743`, 약 1.1GB)을 Drive `store-search-ai/runs/finetune/`에서 **폴더째**(zip) 받아 푼다. 파일을 하나씩 받으면 `tokenizer.json`·하위 폴더가 빠진다. git에 올리지 않는다.
2. `infra/.env`의 `EMBEDDING_MODEL_DIR`에 그 폴더 경로(비우면 저장소 `models/` 아래)
3. `docker compose up -d`(DB + 임베딩). 처음엔 이미지(torch 포함) 만드느라 몇 분. `curl http://127.0.0.1:8001/health`

- 라이브러리는 학습 때 버전(torch 2.11, transformers 5.18, sentence-transformers 5.7), 모델은 **fp32**로 올린다(명시하지 않으면 저장된 fp16).
- API 서버는 `infra/.env`의 `EMBEDDING_URL`로 부른다. `localhost` 대신 `127.0.0.1`(Windows에서 localhost는 IPv6를 먼저 시도해 1~2초 늦어짐).
- 2026-11-19 이 PC에서 확인: AI 레포와 같은 벡터(코사인 1.000000), 검색어 1개 p50 65ms·p95 94ms(150ms 안), 묶음 약 18개/초(가게 21만 개 약 3.4시간 → 전체 벡터는 Colab).

## 6. 가맹점 첫 적재(다음 단계)

갱신 명령이 생기면 11월 원천을 넣는다. 확인할 것:

- **기존 store_id 이어받기**: 9월 원천의 store_id를 같은 가게 판단(사업자번호 + 가맹점명 + 도로명 주소)으로 맞춰 이어받는다. 평가 정답이 이 번호에 걸려 있다.
- **벡터 22만 개**: 한 사람이 GPU로 만들어 넣고 `pg_dump -Fc`로 덤프 → Drive 공유 → 나머지는 `pg_restore`. 각자 몇 시간씩 돌리지 않는다.
- 적재 뒤 **실제 DB 속도 측정**(1km·10km·시도 전체 검색, `docs/db/DB_DESIGN_WHY.md` 8절). 아직 한 번도 못 한 확인이다.

## 7. 개발 순서(권장)

1. 1절 확인 → 2절 설치 → 3절로 DB 띄우기
2. API 서버 뼈대 + DB 연결(Flyway V1) ✅
3. 기본 데이터 넣기 스크립트, 첫 적재(6절), DB 덤프 공유
4. 임베딩 서버(5절) ✅
5. 평가 "정답 결과 파일" → 자바 검색 이식과 비교 테스트
6. API(P0) → 화면

## 8. 막히면

| 증상 | 확인 |
|---|---|
| Docker Desktop이 `WSL not installed` | 관리자 PowerShell에서 `wsl --install --no-distribution` → 재부팅 |
| 이미지 내려받기 실패·인증서 오류 | 회사 프록시·SSL 검사(1절 ⛔3). Docker에 프록시 설정, 회사 루트 인증서 등록 |
| API 서버가 Flyway 오류로 멈춤 | SQL 오류면 고치고, `checksum mismatch`(받은 V1이 바뀜)면 `docker compose down -v` 뒤 다시 |
| `extension "vector" is not available` | `infra/db/Dockerfile`로 이미지가 만들어졌는지(`docker compose build db`) |
| pgvector 설치 중 `bullseye-pgdg Release` 404 | Debian 11 기반 이미지(`postgis/postgis:16-*`)를 쓰고 있다. `infra/db/Dockerfile`은 Debian 12 기반 pgvector 이미지를 쓴다 |
| `postgis-3.so: undefined symbol` | PostGIS가 이미지의 PostgreSQL보다 새 판으로 빌드됨. Dockerfile에서 `postgresql-16`도 같이 설치(올림)하는지 확인 → `docker compose build --no-cache db` → `down -v` 뒤 다시 |
| DBeaver 등에서 `password authentication failed` | DB를 처음 만든 뒤 `.env`의 `POSTGRES_PASSWORD`를 바꾸면 DB에는 반영되지 않는다(처음 만들 때만 읽음). `docker compose exec db psql -U storesearch -d storesearch -c "ALTER ROLE storesearch PASSWORD '새 비밀번호';"`로 맞추거나 `down -v` 뒤 다시 |
| 메모리 부족으로 컨테이너가 죽음 | `.wslconfig` 메모리, `.env`의 `DB_SHARED_BUFFERS`를 1GB로 |
| 5432 포트 사용 중 | `.env`의 `DB_PORT`를 5433 등으로 |
