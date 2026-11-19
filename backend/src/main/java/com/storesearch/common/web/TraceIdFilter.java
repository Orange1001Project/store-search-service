package com.storesearch.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 모든 요청에 추적 ID를 붙인다: 응답 헤더 X-Trace-Id, 오류 본문 traceId, 서버 로그(MDC traceId)가 같은 값.
 * 화면이 보낸 X-App-Version도 여기서 읽어 둔다(API_RULES.md 7절). 보안 필터보다 먼저 돈다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

	private static final int MAX_APP_VERSION_LENGTH = 20;   // search_log.app_version varchar(20)

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String traceId = RequestContext.newTraceId();
		request.setAttribute(RequestContext.TRACE_ID_ATTR, traceId);
		response.setHeader(RequestContext.TRACE_ID_HEADER, traceId);

		String appVersion = request.getHeader(RequestContext.APP_VERSION_HEADER);
		if (StringUtils.hasText(appVersion)) {
			String trimmed = appVersion.strip();
			request.setAttribute(RequestContext.APP_VERSION_ATTR,
					trimmed.length() > MAX_APP_VERSION_LENGTH ? trimmed.substring(0, MAX_APP_VERSION_LENGTH) : trimmed);
		}

		MDC.put("traceId", traceId);
		try {
			chain.doFilter(request, response);
		}
		finally {
			MDC.remove("traceId");
		}
	}

}
