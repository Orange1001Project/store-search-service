# 가맹점 첫 적재 (데이터 버전 1)

> 11월 원천(`20261113_온누리가맹점리스트.xlsx`)을 데이터 버전 1로 넣는다. 벡터 22만 개는 CPU로 몇 시간이라 Colab GPU에서 만든다.
> 규칙(같은 가게 판단·합치기·좌표 오류·id 이어받기): [`DB_TABLES.md`](../db/DB_TABLES.md) 3절. 코드: `backend/.../refresh/`(순서), `store/service/`(값 계산).
> 2026-11-20 이 PC에서 확인: prepare 약 1분 40초, import(시험 벡터) 약 3분 30초, 스냅샷 1.3GB.

## 한눈에

```
[로컬] load-prepare ── store 225,152행 + 버전(building) + data/load/embed_input.csv
   ↓ embed_input.csv를 Drive에 올림
[Colab] embedding-server/colab/embed_stores.ipynb ── embeddings.npy + embeddings.json
   ↓ 두 파일을 data/load/에 내려받음
[로컬] load-import ── 표본 32개를 로컬 임베딩 서버와 비교 → 스냅샷 파티션 → 버전 활성
   ↓
[로컬] pg_dump → Drive 공유(다른 사람은 복원만)
```

**prepare 뒤에는 import가 끝날 때까지 DB를 지우지 않는다.** 새 가게(약 2만 3천 곳)의 id는 prepare 때 새로 발급되므로, 다시 prepare하면 `embed_input.csv`가 바뀌어 Colab을 다시 돌려야 한다(import가 sha256으로 막는다).

## 0. 준비

| 무엇 | 어디서 | 로컬 위치(예) |
|---|---|---|
| 11월 원천 엑셀 | Drive(사업자번호 포함, **git 금지**) | `C:/…/데이터/20261113_온누리가맹점리스트.xlsx` |
| 9월 원천 엑셀 | Drive | `C:/…/데이터/stores_20260907.xlsx` |
| AI 레지스트리 id(사업자번호 열 뺀 CSV) | AI 레포에서 아래 명령 | `data/legacy/store_registry_ids.csv` |
| 평가 정답 | AI 레포 `benchmark/service_ko_v1/qrels.csv` | `data/legacy/service_ko_v1_qrels.csv` |

`data/`는 git에서 빠져 있다(`.gitignore`). AI 레포 폴더에서:

```powershell
.venv\Scripts\python -c "import pandas as pd; r=pd.read_parquet('data/registry/store_registry.parquet'); r[['entity_fingerprint','store_id','created_dataset_version']].to_csv('../store-search-service/data/legacy/store_registry_ids.csv', index=False)"
```

DB와 임베딩 서버가 떠 있어야 한다(`infra`에서 `docker compose up -d`). DB는 비어 있어야 한다(처음부터면 `docker compose down -v` → `up -d` → API 서버 한 번 실행해 Flyway 적용).

## 1. prepare (로컬)

`backend` 폴더에서:

```powershell
.\gradlew.bat bootRun --args="--app.job=load-prepare --app.load.source=C:/…/20261113_온누리가맹점리스트.xlsx --app.load.legacy-source=C:/…/stores_20260907.xlsx --app.load.legacy-registry=../data/legacy/store_registry_ids.csv --app.load.legacy-qrels=../data/legacy/service_ko_v1_qrels.csv"
```

결과(`data/load/`):

| 파일 | 내용 |
|---|---|
| `embed_input.csv` | store_id, doc_text(store_id 순). **Colab에 올릴 파일** |
| `prepare_report.json` | 건수(원천 행, 가게, 합친 곳, 이어받은 id, 새 id, 좌표 오류), 건너뛴 행 번호 |
| `store_id_aliases.csv` | 가게 하나로 합쳐져 버려진 9월 id → 남긴 id(평가 때 바꿔 읽기) |

2026-11-20 결과: 원천 225,807행 중 2행은 상호가 없어 건너뜀(엑셀이 "+상호"를 수식으로 바꿔 `#NAME?`이 된 셀) → 가게 225,152곳(652곳에서 1,305행을 합침), 9월 id 이어받음 202,168, 새 id 22,984, 좌표 오류 475. 9월 행은 모두 AI id를 찾았다.

원천 셀은 AI 레포(pandas)가 읽은 값과 같게 읽는다: 날짜 서식 숫자는 `2026-03-03 00:00:00`, 오류 값은 빈 값, `&gt；`처럼 세미콜론 없는 엔티티도 푼다.

## 2. Colab

1. Drive `내 드라이브/store-search-ai/service_load/v1/`에 `embed_input.csv`를 올린다(모델은 `runs/finetune/bge_m3_ft_mj_20261014_0743/`에 이미 있음).
2. `embedding-server/colab/embed_stores.ipynb`를 Colab에서 열고 런타임을 GPU로 → 위에서부터 실행.
3. 같은 폴더에 생긴 `embeddings.npy`(약 920MB)와 `embeddings.json`을 로컬 `data/load/`에 내려받는다.

## 3. import (로컬)

```powershell
.\gradlew.bat bootRun --args="--app.job=load-import"
```

멈추는 경우: 입력 파일 sha256·모델 이름·건수·차원이 다름, DB 값과 doc_text가 다름, 정규화 안 된 벡터, **표본 32개의 로컬 서버 대비 코사인 < 0.999**, 전환 전 검증(없는 가게·업종·결제) 실패. 멈추면 아무것도 붙지 않고 버전은 building 그대로라 고쳐서 다시 import하면 된다. 결과는 `data/load/import_report.json`.

확인:

```sql
SELECT version_name, status, n_total, n_coord_error FROM data_version;          -- active, 225152, 475
SELECT count(*), count(*) FILTER (WHERE searchable) FROM store_snapshot;        -- 225152, 224677
```

## 4. 덤프 공유

`infra` 폴더에서(PowerShell의 `>`는 바이너리를 깨뜨리므로 컨테이너 안에서 파일로 쓴다):

```powershell
docker compose exec db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -f /tmp/storesearch_v1.dump'
docker compose cp db:/tmp/storesearch_v1.dump ../data/storesearch_v1.dump
```

약 1.2GB, 2분 반. Drive `store-search-ai/service_load/`에 올린다.

**받는 사람**(빈 DB에, API 서버를 띄우기 전에):

```powershell
docker compose down -v
docker compose up -d db
docker compose cp ../data/storesearch_v1.dump db:/tmp/storesearch_v1.dump
docker compose exec db sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner /tmp/storesearch_v1.dump'
```

약 30초. Flyway 기록도 같이 들어가므로 그 뒤 API 서버를 그대로 띄운다. V1이 그 뒤 바뀌었으면 덤프를 다시 받는다.
