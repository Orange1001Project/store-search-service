package com.storesearch.common.security;

import com.storesearch.api.model.ErrorCode;
import com.storesearch.common.error.ErrorResponseWriter;
import com.storesearch.common.web.WebConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import org.springframework.lang.Nullable;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * CSRF 검사(NFR-SEC-02, API_RULES.md 5절): 쿠키로 인증하는 쓰기 요청(POST·PUT·PATCH·DELETE)은
 * CSRF 쿠키 값을 X-CSRF-Token 헤더에 그대로 넣어 보내야 한다. 다르거나 없으면 403 CSRF_FAILED.
 * <ul>
 * <li>관리자(/api/v1/admin/**): 관리자 세션 쿠키가 있을 때 admin_csrf_token과 비교. 로그인 요청은 제외</li>
 * <li>회원(그 밖): 회원 인증 쿠키(접근·갱신·가입 대기)가 있을 때 csrf_token과 비교</li>
 * </ul>
 * 인증 쿠키가 없는 요청(비회원 검색·이벤트, 로그인 전)은 검사하지 않는다 — 막을 대상이 없고, 회원 API라면 401이 먼저 맞다.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

	private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

	private static final String ADMIN_PREFIX = WebConfig.API_PREFIX + "/admin/";

	private static final String ADMIN_LOGIN = WebConfig.API_PREFIX + "/admin/auth/login";

	/** 이벤트 수집은 sendBeacon이라 회원 쿠키·CSRF 헤더를 요구하지 않는다(API_RULES.md 5절). */
	private static final String EVENTS = WebConfig.API_PREFIX + "/events";

	private final ErrorResponseWriter errorWriter;

	public CsrfCookieFilter(ErrorResponseWriter errorWriter) {
		this.errorWriter = errorWriter;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String uri = request.getRequestURI();
		return SAFE_METHODS.contains(request.getMethod()) || !uri.startsWith(WebConfig.API_PREFIX + "/")
				|| uri.equals(ADMIN_LOGIN) || uri.equals(EVENTS);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		boolean admin = request.getRequestURI().startsWith(ADMIN_PREFIX);
		boolean cookieAuthenticated = admin ? cookie(request, AuthCookies.ADMIN_SESSION) != null
				: AuthCookies.MEMBER_AUTH.stream().anyMatch(name -> cookie(request, name) != null);
		if (cookieAuthenticated) {
			String expected = cookie(request, admin ? AuthCookies.ADMIN_CSRF : AuthCookies.MEMBER_CSRF);
			String actual = request.getHeader(AuthCookies.CSRF_HEADER);
			if (!matches(expected, actual)) {
				errorWriter.write(request, response, ErrorCode.CSRF_FAILED, "CSRF 토큰이 없거나 맞지 않아요");
				return;
			}
		}
		chain.doFilter(request, response);
	}

	private static boolean matches(@Nullable String expected, @Nullable String actual) {
		if (expected == null || expected.isEmpty() || actual == null) {
			return false;
		}
		return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
	}

	private static @Nullable String cookie(HttpServletRequest request, String name) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie c : cookies) {
			if (name.equals(c.getName())) {
				return c.getValue();
			}
		}
		return null;
	}

}
