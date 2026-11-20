package com.storesearch.common.error;

import com.storesearch.api.model.ErrorCode;
import com.storesearch.api.model.ErrorResponse;
import com.storesearch.api.model.ErrorResponseDetails;
import com.storesearch.common.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** 오류 본문(API_RULES.md 4절)을 만든다. 컨트롤러 밖(보안 필터 등)에서는 {@link #write}로 바로 쓴다. */
@Component
public class ErrorResponseWriter {

	private final JsonMapper jsonMapper;

	public ErrorResponseWriter(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public static ErrorResponse body(HttpServletRequest request, ErrorCode code, String message,
			@Nullable ErrorResponseDetails details) {
		ErrorResponse body = new ErrorResponse(code, message, RequestContext.traceId(request));
		body.setDetails(details);
		return body;
	}

	public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String message)
			throws IOException {
		response.setStatus(ErrorStatus.of(code).value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		jsonMapper.writeValue(response.getOutputStream(), body(request, code, message, null));
	}

}
