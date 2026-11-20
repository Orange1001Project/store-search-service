package com.storesearch.common.error;

import com.storesearch.api.model.ErrorCode;
import com.storesearch.api.model.ErrorResponseDetails;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;

/**
 * 서비스 코드가 던지는 오류. 응답은 {@link GlobalExceptionHandler}가 API_RULES.md 4절 모양으로 만든다.
 * 예: {@code throw new ApiException(ErrorCode.STORE_NOT_FOUND, "가게가 없어요")}
 */
public class ApiException extends RuntimeException {

	private final ErrorCode code;

	private final @Nullable ErrorResponseDetails details;

	public ApiException(ErrorCode code, String message) {
		this(code, message, null);
	}

	public ApiException(ErrorCode code, String message, @Nullable ErrorResponseDetails details) {
		super(message);
		this.code = code;
		this.details = details;
	}

	public ErrorCode getCode() {
		return code;
	}

	public HttpStatus getStatus() {
		return ErrorStatus.of(code);
	}

	public @Nullable ErrorResponseDetails getDetails() {
		return details;
	}

}
