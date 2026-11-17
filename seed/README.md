# seed — 서비스가 쓰는 작은 기준 데이터

AI 레포(store-search)에서 만든 파일을 옮겨 둔 것이다. 바꿀 때는 버전 이름을 올려 새 파일로 더한다(예: `brand_alias_v2.csv`). 검색 결과가 이 값들로 평가됐기 때문이다([검색 인계](../docs/search/SEARCH_HANDOFF.md)).

| 파일 | 내용 | 쓰는 곳 | AI 레포 원래 위치 |
|---|---|---|---|
| `category/taxonomy_v1.yaml` | 온누리 업종 중분류 17개(K001~K017, 2026-11-13 공식) | `category` 표(`schema.sql`에 이미 들어 있음) | `configs/category/` |
| `category/ksic_onnuri_v1.csv` | 산업분류(KSIC) → 업종 매핑 1,261행. 원천에 업종이 비었을 때 | 가맹점 적재 | `configs/category/` |
| `service/brand_alias_v1.csv` | 브랜드 별칭 65행 | `brand_alias` 표 | `configs/service/` |
| `service/search_policy_v1.yaml` | 검색 정책 값(반경, 문턱값 등) | API 서버가 파일로 읽음, 버전만 DB·로그 | `configs/service/` |
| `service/calibrator.json` | 관련 확률 계수 | API 서버가 파일로 읽음, 버전만 DB·로그 | `results/service_pipeline/x1/` |

큰 데이터(지명 사전 parquet, 확장어 캐시, 원천 엑셀, 임베딩 모델)는 git에 두지 않는다. 받는 곳은 [`docs/dev/LOCAL_DEV_SETUP.md`](../docs/dev/LOCAL_DEV_SETUP.md) 4절.
