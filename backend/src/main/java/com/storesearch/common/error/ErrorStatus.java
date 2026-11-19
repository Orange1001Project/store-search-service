package com.storesearch.common.error;

import com.storesearch.api.model.ErrorCode;
import org.springframework.http.HttpStatus;

/** 오류 코드 → HTTP 상태(API_RULES.md 4절 표). 코드를 더하면 여기도 더한다. */
public final class ErrorStatus {

	private ErrorStatus() {
	}

	public static HttpStatus of(ErrorCode code) {
		return switch (code) {
			case INVALID_REQUEST, PASSWORD_POLICY, CURRENT_PASSWORD_WRONG, INVALID_FILE, TERMS_REQUIRED -> HttpStatus.BAD_REQUEST;
			case AUTH_REQUIRED, TOKEN_EXPIRED, INVALID_CREDENTIALS, SIGNUP_EXPIRED, REFRESH_REUSED -> HttpStatus.UNAUTHORIZED;
			case CSRF_FAILED, ACCOUNT_LOCKED, ACCOUNT_DISABLED, PASSWORD_CHANGE_REQUIRED -> HttpStatus.FORBIDDEN;
			case STORE_NOT_FOUND, NOT_FOUND -> HttpStatus.NOT_FOUND;
			case REFRESH_ALREADY_RUNNING, SAME_FILE_ALREADY_APPLIED, INVALID_STATE, FAVORITE_LIMIT, ALREADY_MEMBER -> HttpStatus.CONFLICT;
			case SEARCH_RESULT_EXPIRED -> HttpStatus.GONE;
			case FILE_TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
			case SOURCE_COLUMNS_MISMATCH -> HttpStatus.UNPROCESSABLE_CONTENT;
			case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
			case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
			case SERVICE_UNAVAILABLE, MAINTENANCE -> HttpStatus.SERVICE_UNAVAILABLE;
		};
	}

}
