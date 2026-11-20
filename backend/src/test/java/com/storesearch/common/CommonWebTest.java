package com.storesearch.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.storesearch.TestcontainersConfig;
import com.storesearch.api.model.LatLng;
import com.storesearch.api.model.PaymentCode;
import com.storesearch.api.model.StoreCardBase;
import com.storesearch.common.json.KstTime;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.json.JsonMapper;

/** 공통 모듈: 오류 형식, 추적 ID, 익명 세션, 접근 규칙, CSRF, JSON 빈 값·시각 규칙. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class CommonWebTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JsonMapper json;

	// ── 오류 형식 ──

	@Test
	void apiExceptionBecomesErrorBodyWithTraceId() {
		MvcTestResult result = mvc.get().uri("/api/v1/test/not-found").exchange();

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("STORE_NOT_FOUND");
		String traceId = result.getResponse().getHeader("X-Trace-Id");
		assertThat(traceId).hasSize(16);
		assertThat(result).bodyJson().extractingPath("$.traceId").isEqualTo(traceId);
		assertThat(result).bodyJson().extractingPath("$.message").asString().isNotEmpty();
		assertThat(result).bodyJson().doesNotHavePath("$.details");
	}

	@Test
	void unexpectedErrorIs500InternalError() {
		MvcTestResult result = mvc.get().uri("/api/v1/test/boom").exchange();

		assertThat(result).hasStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INTERNAL_ERROR");
	}

	@Test
	void unknownPathIs404NotFound() {
		MvcTestResult result = mvc.get().uri("/api/v1/nothing-here").exchange();

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("NOT_FOUND");
	}

	@Test
	void validationErrorsListEachField() {
		MvcTestResult result = mvc.post().uri("/api/v1/test/location")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"lat\": 100}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("INVALID_REQUEST");
		assertThat(result).bodyJson().extractingPath("$.details.fields").asArray()
			.containsExactlyInAnyOrder(Map.of("field", "lat", "reason", "OUT_OF_RANGE"),
					Map.of("field", "lng", "reason", "REQUIRED"));
	}

	@Test
	void wrongTypeInBodyNamesTheField() {
		MvcTestResult result = mvc.post().uri("/api/v1/test/location")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"lat\": \"north\", \"lng\": 127}")
			.exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.details.fields[0]")
			.isEqualTo(Map.of("field", "lat", "reason", "INVALID_FORMAT"));
	}

	@Test
	void lowercaseEnumQueryValueIsConverted() {
		assertThat(mvc.get().uri("/api/v1/test/status?status=pending").exchange())
			.hasStatusOk().bodyJson().extractingPath("$.status").isEqualTo("PENDING");

		MvcTestResult bad = mvc.get().uri("/api/v1/test/status?status=nope").exchange();
		assertThat(bad).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(bad).bodyJson().extractingPath("$.details.fields[0]")
			.isEqualTo(Map.of("field", "status", "reason", "INVALID_FORMAT"));
	}

	// ── 익명 세션 ──

	@Test
	void anonymousSessionIsIssuedAndKept() {
		MvcTestResult first = mvc.get().uri("/api/v1/test/status?status=active").exchange();
		Cookie sid = first.getResponse().getCookie("sid");
		assertThat(sid).isNotNull();
		assertThat(sid.isHttpOnly()).isTrue();
		assertThat(sid.getSecure()).isTrue();
		assertThat(sid.getMaxAge()).isEqualTo(1800);
		assertThat(UUID.fromString(sid.getValue())).isNotNull();

		MvcTestResult again = mvc.get().uri("/api/v1/test/status?status=active").cookie(sid).exchange();
		assertThat(again.getResponse().getCookie("sid").getValue()).isEqualTo(sid.getValue());
	}

	@Test
	void noSessionCookieOutsideApi() {
		assertThat(mvc.get().uri("/actuator/health").exchange().getResponse().getCookie("sid")).isNull();
	}

	// ── 접근 규칙·CSRF ──

	@Test
	void memberApiWithoutLoginIs401() {
		MvcTestResult result = mvc.get().uri("/api/v1/me").exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("AUTH_REQUIRED");
	}

	@Test
	void adminApiWithoutLoginIs401() {
		assertThat(mvc.get().uri("/api/v1/admin/stores").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void cookieAuthenticatedWriteNeedsMatchingCsrfHeader() {
		Cookie access = new Cookie("access_token", "x");
		Cookie csrf = new Cookie("csrf_token", "token-1");

		MvcTestResult missing = mvc.post().uri("/api/v1/auth/test-write").cookie(access, csrf).exchange();
		assertThat(missing).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(missing).bodyJson().extractingPath("$.code").isEqualTo("CSRF_FAILED");

		assertThat(mvc.post().uri("/api/v1/auth/test-write").cookie(access, csrf).header("X-CSRF-Token", "other").exchange())
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.post().uri("/api/v1/auth/test-write").cookie(access, csrf).header("X-CSRF-Token", "token-1").exchange())
			.hasStatusOk();
	}

	@Test
	void adminCsrfUsesItsOwnCookie() {
		Cookie session = new Cookie("admin_sid", "s");
		Cookie memberCsrf = new Cookie("csrf_token", "token-1");

		MvcTestResult result = mvc.post().uri("/api/v1/admin/stores/x/visibility")
			.cookie(session, memberCsrf).header("X-CSRF-Token", "token-1").exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(result).bodyJson().extractingPath("$.code").isEqualTo("CSRF_FAILED");
	}

	@Test
	void logoutNeverAnswers401ButChecksCsrfWhenCookiePresent() {
		// 세션 쿠키가 없으면 인증·CSRF 모두 건너뛴다(로그아웃은 실패하지 않는다)
		assertThat(mvc.post().uri("/api/v1/admin/auth/logout").exchange()).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(mvc.post().uri("/api/v1/auth/logout").exchange()).hasStatus(HttpStatus.NO_CONTENT);

		// 만료된 세션이라도 쿠키가 있으면 CSRF는 검사한다
		Cookie session = new Cookie("admin_sid", "expired");
		Cookie adminCsrf = new Cookie("admin_csrf_token", "a-1");
		MvcTestResult noHeader = mvc.post().uri("/api/v1/admin/auth/logout").cookie(session, adminCsrf).exchange();
		assertThat(noHeader).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(noHeader).bodyJson().extractingPath("$.code").isEqualTo("CSRF_FAILED");
		assertThat(mvc.post().uri("/api/v1/admin/auth/logout").cookie(session, adminCsrf).header("X-CSRF-Token", "a-1")
			.exchange()).hasStatus(HttpStatus.NO_CONTENT);

		Cookie refresh = new Cookie("refresh_token", "r");
		Cookie memberCsrf = new Cookie("csrf_token", "m-1");
		assertThat(mvc.post().uri("/api/v1/auth/logout").cookie(refresh, memberCsrf).exchange())
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void writesWithoutAuthCookieAndEventsSkipCsrf() {
		assertThat(mvc.post().uri("/api/v1/auth/test-write").exchange()).hasStatusOk();
		assertThat(mvc.post().uri("/api/v1/events").cookie(new Cookie("access_token", "x")).exchange()).hasStatusOk();
	}

	// ── JSON ──

	@Test
	void requiredNullableFieldsStayAsNullAndOptionalNullsAreDropped() {
		StoreCardBase card = new StoreCardBase(UUID.randomUUID(), "열매서점", "K003", "생활·잡화", null, null, "대전 중구 대종로",
				new LatLng(36.3504, 127.3845), List.of(PaymentCode.CARD));

		String body = json.writeValueAsString(card);

		assertThat(body).contains("\"marketName\":null", "\"marketType\":null", "\"paymentCodes\":[\"card\"]");
		assertThat(body).doesNotContain("favorited");
	}

	@Test
	void timesAreWrittenWithKstOffset() {
		String body = json.writeValueAsString(Map.of("at", KstTime.toApi(Instant.parse("2026-11-13T05:30:00Z"))));

		assertThat(body).isEqualTo("{\"at\":\"2026-11-13T14:30:00+09:00\"}");
	}

}
