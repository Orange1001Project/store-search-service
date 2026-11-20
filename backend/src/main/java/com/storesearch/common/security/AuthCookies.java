package com.storesearch.common.security;

import java.util.Set;

/** 인증 쿠키 이름(API_RULES.md 5절). 로그인 구현이 이 이름으로 쿠키를 주고, 보안 설정이 이 이름으로 읽는다. */
public final class AuthCookies {

	/** 회원 접근 토큰(JWT 30분, 쿠키 수명 14일). */
	public static final String ACCESS_TOKEN = "access_token";

	/** 회원 갱신 토큰(14일, 경로 /api/v1/auth). */
	public static final String REFRESH_TOKEN = "refresh_token";

	/** 처음 온 사람·약관이 바뀐 회원의 가입 대기(10분). */
	public static final String SIGNUP_TOKEN = "signup_token";

	/** 회원 CSRF 토큰(화면이 읽음, 14일). */
	public static final String MEMBER_CSRF = "csrf_token";

	/** 관리자 서버 세션(경로 /api/v1/admin). 이름은 openapi.yaml securitySchemes.adminSession과 같아야 한다. */
	public static final String ADMIN_SESSION = "admin_sid";

	/** 관리자 CSRF 토큰(화면이 읽음, 세션 쿠키). */
	public static final String ADMIN_CSRF = "admin_csrf_token";

	/** 화면이 CSRF 쿠키 값을 넣어 보내는 헤더. */
	public static final String CSRF_HEADER = "X-CSRF-Token";

	static final Set<String> MEMBER_AUTH = Set.of(ACCESS_TOKEN, REFRESH_TOKEN, SIGNUP_TOKEN);

	private AuthCookies() {
	}

}
