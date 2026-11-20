package com.storesearch.common.security;

import com.storesearch.api.model.ErrorCode;
import com.storesearch.common.error.ErrorResponseWriter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * 경로별 접근 규칙(API_RULES.md 5절).
 * <ul>
 * <li>공개: 메타·검색·조회·상세·이벤트·소셜 로그인·회원 로그아웃, 관리자 로그인·로그아웃, health</li>
 * <li>회원만: /api/v1/me/** — 회원 인증(JWT 쿠키)은 로그인 구현 때 이 체인에 필터로 더한다</li>
 * <li>관리자만: /api/v1/admin/** — 관리자 세션도 로그인 구현 때 더한다</li>
 * </ul>
 * 인증이 없으면 401 AUTH_REQUIRED, CSRF가 틀리면 403 CSRF_FAILED(본문은 공통 오류 형식).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication   // 적재 명령(app.job)처럼 웹 서버 없이 뜰 때는 만들지 않는다
public class SecurityConfig {

	@Bean
	SecurityFilterChain apiSecurity(HttpSecurity http, ErrorResponseWriter errorWriter) throws Exception {
		http.csrf(AbstractHttpConfigurer::disable)   // 쿠키 이름이 둘이라 CsrfCookieFilter가 직접 검사
			.formLogin(AbstractHttpConfigurer::disable)
			.httpBasic(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.requestCache(AbstractHttpConfigurer::disable)
			.authorizeHttpRequests(auth -> auth
				.requestMatchers("/api/v1/me/**").authenticated()
				.requestMatchers("/api/v1/admin/auth/login").permitAll()
				// 로그아웃은 세션이 이미 끝났어도 204(401 없음). 세션 쿠키가 있으면 CsrfCookieFilter가 CSRF는 검사한다
				.requestMatchers("/api/v1/admin/auth/logout").permitAll()
				.requestMatchers("/api/v1/admin/**").authenticated()
				.anyRequest().permitAll())
			.exceptionHandling(e -> e
				.authenticationEntryPoint((request, response, ex) ->
						errorWriter.write(request, response, ErrorCode.AUTH_REQUIRED, "로그인이 필요해요"))
				.accessDeniedHandler((request, response, ex) ->
						errorWriter.write(request, response, ErrorCode.AUTH_REQUIRED, "권한이 없어요")))
			.addFilterBefore(new CsrfCookieFilter(errorWriter), AuthorizationFilter.class);
		return http.build();
	}

	/** 아이디·비밀번호 기본 사용자를 만들지 않게 한다(관리자 로그인은 직접 구현). */
	@Bean
	UserDetailsService noDefaultUsers() {
		return username -> {
			throw new UsernameNotFoundException(username);
		};
	}

}
