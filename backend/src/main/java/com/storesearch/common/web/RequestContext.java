package com.storesearch.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.lang.Nullable;

/** 필터가 요청에 붙여 둔 값(추적 ID, 익명 세션, 화면 버전)을 꺼내 쓰는 곳. */
public final class RequestContext {

	public static final String TRACE_ID_HEADER = "X-Trace-Id";

	public static final String APP_VERSION_HEADER = "X-App-Version";

	static final String TRACE_ID_ATTR = RequestContext.class.getName() + ".traceId";

	static final String SESSION_ID_ATTR = RequestContext.class.getName() + ".sessionId";

	static final String APP_VERSION_ATTR = RequestContext.class.getName() + ".appVersion";

	private RequestContext() {
	}

	/** 응답 헤더 X-Trace-Id와 오류 본문 traceId에 쓰는 값. 필터를 거치지 않은 요청이면 새로 만든다. */
	public static String traceId(HttpServletRequest request) {
		Object value = request.getAttribute(TRACE_ID_ATTR);
		if (value instanceof String s) {
			return s;
		}
		String created = newTraceId();
		request.setAttribute(TRACE_ID_ATTR, created);
		return created;
	}

	/** 익명 세션 sid(로그용, 회원 ID와 잇지 않음). /api 밖 요청이면 null. */
	public static @Nullable UUID sessionId(HttpServletRequest request) {
		return request.getAttribute(SESSION_ID_ATTR) instanceof UUID id ? id : null;
	}

	/** 화면이 보낸 X-App-Version(없으면 null). 검색 로그 app_version에 남긴다. */
	public static @Nullable String appVersion(HttpServletRequest request) {
		return request.getAttribute(APP_VERSION_ATTR) instanceof String s ? s : null;
	}

	static String newTraceId() {
		return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
	}

}
