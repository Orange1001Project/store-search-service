# backend — API 서버 (Spring Boot 4, Java 21)

## 실행

```bash
# DB 먼저: infra 폴더에서 docker compose up -d db
./gradlew bootRun   # 처음 뜰 때 Flyway가 표를 만든다. DB 접속 값은 ../infra/.env에서 읽음
./gradlew test      # Docker 필요(테스트가 빈 DB 컨테이너를 띄움)
```

## 폴더 구조: 기능(도메인)별로 나누고, 그 안을 역할별로

```
com.storesearch
├─ common/       여러 기능이 같이 쓰는 것: error(오류 형식) web(추적 ID·세션·경로) security(접근 규칙·CSRF) json(빈 값·시각)
├─ reference/    기준 데이터: 업종·결제·브랜드 별칭·지명 사전·확장어 캐시, GET /meta
├─ embedding/    임베딩 서버 호출(검색·적재가 같이 씀)
├─ store/        가맹점 원천·상태(store, store_override), GET /stores/{id}
├─ search/       검색·지도 조회·다음 페이지, 검색어 해석·BM25·점수(메모리)
├─ member/       소셜 로그인·토큰·내 정보·최근 검색어·관심 가맹점
├─ admin/        관리자 로그인·가맹점 수정·숨김·통계
├─ refresh/      데이터 버전·적재·주간 갱신(명령으로 실행)
└─ log/          검색 로그·행동 이벤트

각 기능 폴더 안(필요한 것만 만든다):
  controller/  생성된 *Api 인터페이스를 구현. 요청 받기 → service 호출 → 응답. 로직 없음
  service/     업무 규칙과 트랜잭션(@Transactional). entity ↔ API DTO 변환도 여기서
  repository/  DB 읽고 쓰기. 보통은 Spring Data JPA 인터페이스, PostGIS·pgvector 쿼리는 JdbcTemplate
  entity/      DB 표 한 행 = 객체 하나(JPA @Entity). API로 그대로 내보내지 않는다
  dto/         기능 안에서만 쓰는 값(조회 결과, 서비스 사이 전달). API 요청·응답 DTO는 명세에서 생성됨
```

API 요청·응답 DTO와 컨트롤러 인터페이스는 `docs/api/openapi.yaml`에서 빌드 때 생성된다(`build/generated/openapi`, 패키지 `com.storesearch.api`·`com.storesearch.api.model`). 직접 만들지 않는다.

## 요청이 지나가는 길 (예: GET /api/v1/stores/{storeId})

```
브라우저
 → 필터: TraceIdFilter(X-Trace-Id) → AnonymousSessionFilter(sid) → 보안(로그인·CSRF 검사)
 → StoreController.getStore(storeId)          implements StoresApi
 → StoreService.getStore(storeId)             @Transactional(readOnly = true)
 → StoreSnapshotRepository … → DB
 ← entity/조회 결과 → service가 StoreDetail(생성된 DTO)로 바꿈
 ← ResponseEntity<StoreDetail> → JSON
없으면 service가 throw new ApiException(ErrorCode.STORE_NOT_FOUND, "…") → GlobalExceptionHandler가 404 오류 형식으로
```

## 규칙

- 의존 방향은 controller → service → repository 한쪽으로만. controller가 repository를 직접 부르지 않는다.
- 다른 기능의 데이터가 필요하면 그 기능의 **service**를 부른다(남의 repository·entity를 직접 쓰지 않는다). 기능 사이 연결은 JPA 관계 대신 ID(예: 관심 가맹점의 store_id)로.
- 의존성은 생성자 주입(`private final` 필드 + 생성자). `@Autowired` 필드 주입은 쓰지 않는다.
- entity는 API로 내보내지 않는다(표가 바뀌면 API가 같이 깨지고, 내부 열이 새어 나감).
- 표 구조는 Flyway SQL(`src/main/resources/db/migration`)로만 바꾼다. JPA가 표를 만들지 않는다(`ddl-auto: none`).
- 오류는 `ApiException(ErrorCode, 메시지)`로 던진다. 오류 코드는 명세 `ErrorCode`에 있는 것만.
- `util` 폴더는 만들지 않는다. 도우미는 쓰는 곳 가까이(여럿이 쓰면 `common/<주제>`).

## 테스트

| 종류 | 언제 | 방법 |
|---|---|---|
| 단위 | 검색어 해석·점수 계산처럼 DB 없는 로직 | 일반 JUnit. Spring을 띄우지 않아 빠름 |
| 통합 | API + DB | `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Import(TestcontainersConfig.class)`. 필요한 데이터는 테스트가 넣는다 |

테스트 폴더는 `src/main`과 같은 패키지 구조로 둔다(예: `store/service/StoreServiceTest`).
