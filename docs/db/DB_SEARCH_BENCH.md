# DB 검색 조회 속도 측정

> 왜: HNSW 없이 "범위로 거른 뒤 정확 계산"이 충분히 빠른지([`DB_DESIGN_WHY.md`](DB_DESIGN_WHY.md) 8절)를 실제 데이터(데이터 버전 1, 검색 대상 224,677곳)로 확인한다.
> 노트북: [`infra/colab/db_search_bench.ipynb`](../../infra/colab/db_search_bench.ipynb) — Colab VM에 PostgreSQL 16 + PostGIS + pgvector를 깔고 덤프를 복원해 잰다(로컬 PC 발열을 피하려고).

## 무엇을 재나

평가 질의 695개(AI 레포 `service_queries_v1.csv`: 검색어 + 검색 위치)를 임베딩 모델로 벡터로 만들어, [`DB_TABLES.md`](DB_TABLES.md) 10-1절 쿼리를 그대로 보낸다.

| 경우 | 쿼리 |
|---|---|
| ① 반경 1km / 10km | 검색 중심 원 안 벡터 상위 50 |
| ② 시군구 / 시도 | 검색 위치의 법정동 코드 앞 5 / 2자리 전체에서 상위 50 |
| ③ 지도 도시 화면(약 5×4km) / 넓은 화면(약 27×22km) | 화면 사각형 안 상위 50 |
| ⑤ 검색어 없는 도시 / 넓은 화면 | 화면 안 가까운 순 300 |
| 가장 무거운 경우 | 가게가 가장 많은 시도 전체, 서울에서 가장 빽빽한 곳 10km |
| 동시 부하 | 초당 20건 × 60초(NFR-PRF-03), 검색 1건 = 1km + 10km |

각 경우를 DB 재시작 직후(1회차)와 메모리에 올라온 뒤(2회차) 두 번 잰다.

## 기준

검색 전체 목표는 서버 처리 p95 ≤ 300ms(NFR-PRF-01)다. 임베딩 서버(p95 94ms)·BM25·순위 계산이 같이 들어가므로 **DB 쿼리 하나는 p95 ≤ 150ms**를 목표로 본다.

넘는 경우가 있으면 차례로: ① 병렬 쿼리(`max_parallel_workers_per_gather`) ② 앱 메모리에 벡터를 올려 범위 안만 계산 ③ 넓은 범위에만 HNSW(근사라 이식 검증을 다시 함).

## 실행

1. Drive `store-search-ai/service_load/`에 `storesearch_v1.dump`(덤프, [`DATA_LOAD.md`](../dev/DATA_LOAD.md) 4절), `service_load/bench/`에 `service_queries_v1.csv`(로컬 `data/bench/` 또는 AI 레포 `data/query/`)
2. Colab에서 노트북 열기 → 런타임 GPU(T4) → 위에서부터 실행(약 15~20분)
3. 결과 `service_load/bench/db_search_<시각>.md`를 아래 "결과"에 옮긴다

Colab CPU는 보통 2개라 실제 서버보다 적을 수 있다. 그래서 여기 숫자는 **보수적인 값**으로 본다. 클라이언트와 DB가 같은 VM이라 "API 서버와 DB가 한 서버"인 구성과 비슷하다.

## 결과

(Colab 실행 후 채움)
