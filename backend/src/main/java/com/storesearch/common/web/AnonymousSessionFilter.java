package com.storesearch.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 익명 세션 쿠키 sid(FR-LOG-05, API_RULES.md 5절). 로그용이고 회원 ID와 잇지 않는다.
 * 요청마다 수명을 30분으로 다시 늘려서, 30분 동안 요청이 없으면 쿠키가 사라지고 다음 요청에 새 sid를 받는다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AnonymousSessionFilter extends OncePerRequestFilter {

	public static final String COOKIE_NAME = "sid";

	static final Duration IDLE_TIMEOUT = Duration.ofMinutes(30);

	private final boolean secureCookie;

	public AnonymousSessionFilter(@Value("${app.cookie.secure:true}") boolean secureCookie) {
		this.secureCookie = secureCookie;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		UUID sid = readSid(request);
		if (sid == null) {
			sid = UUID.randomUUID();
		}
		request.setAttribute(RequestContext.SESSION_ID_ATTR, sid);
		response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE_NAME, sid.toString())
			.path("/")
			.httpOnly(true)
			.secure(secureCookie)
			.sameSite("Lax")
			.maxAge(IDLE_TIMEOUT)
			.build()
			.toString());
		chain.doFilter(request, response);
	}

	private static @Nullable UUID readSid(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (COOKIE_NAME.equals(cookie.getName())) {
				try {
					return UUID.fromString(cookie.getValue());
				}
				catch (IllegalArgumentException ignored) {
					return null;   // 형식이 이상하면 새로 준다
				}
			}
		}
		return null;
	}

}
