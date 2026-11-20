package com.storesearch.common.error;

import com.storesearch.api.model.ErrorCode;
import com.storesearch.api.model.ErrorResponse;
import com.storesearch.api.model.ErrorResponseDetails;
import com.storesearch.api.model.FieldError.ReasonEnum;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.databind.DatabindException;

/**
 * 모든 오류를 API_RULES.md 4절 모양({code, message, details, traceId})으로 바꾼다.
 * 입력 검증 실패는 400 INVALID_REQUEST + details.fields[{field, reason}].
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	private static final Set<String> REQUIRED = Set.of("NotNull", "NotBlank", "NotEmpty");

	private static final Set<String> FORMAT = Set.of("Pattern", "Email", "typeMismatch");

	private static final Set<String> RANGE = Set.of("Min", "Max", "DecimalMin", "DecimalMax", "Positive",
			"PositiveOrZero", "Negative", "NegativeOrZero", "Past", "Future", "PastOrPresent", "FutureOrPresent");

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ErrorResponse> handleApi(ApiException e, HttpServletRequest request) {
		if (e.getStatus().is5xxServerError()) {
			log.error("{} {}", e.getCode(), e.getMessage(), e);
		}
		return respond(request, e.getCode(), e.getMessage(), e.getDetails());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
		ErrorResponseDetails details = new ErrorResponseDetails();
		for (FieldError error : e.getBindingResult().getFieldErrors()) {
			details.addFieldsItem(new com.storesearch.api.model.FieldError(error.getField(), reasonOf(error)));
		}
		return invalid(request, details);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	ResponseEntity<ErrorResponse> handleParamValidation(HandlerMethodValidationException e, HttpServletRequest request) {
		ErrorResponseDetails details = new ErrorResponseDetails();
		e.getParameterValidationResults().forEach(result -> {
			String name = result.getMethodParameter().getParameterName();
			MessageSourceResolvable first = result.getResolvableErrors().getFirst();
			details.addFieldsItem(new com.storesearch.api.model.FieldError(name == null ? "" : name,
					reasonOf(first.getCodes(), null, null)));
		});
		return invalid(request, details);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException e,
			HttpServletRequest request) {
		return invalid(request, field(e.getParameterName(), ReasonEnum.REQUIRED));
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
		return invalid(request, field(e.getName(), ReasonEnum.INVALID_FORMAT));
	}

	@ExceptionHandler({ MissingRequestHeaderException.class, MissingRequestCookieException.class })
	ResponseEntity<ErrorResponse> handleMissingHeader(Exception e, HttpServletRequest request) {
		return respond(request, ErrorCode.INVALID_REQUEST, e.getMessage(), null);
	}

	/** JSON 문법 오류, 타입·enum 값이 틀림. 어느 필드인지 알 수 있으면 details.fields에 넣는다. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e, HttpServletRequest request) {
		if (e.getCause() instanceof DatabindException db && !db.getPath().isEmpty()) {
			return invalid(request, field(pathOf(db), ReasonEnum.INVALID_FORMAT));
		}
		return respond(request, ErrorCode.INVALID_REQUEST, "요청 본문을 읽을 수 없어요", null);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException e, HttpServletRequest request) {
		return respond(request, ErrorCode.NOT_FOUND, "없는 주소예요", null);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
			.body(ErrorResponseWriter.body(request, ErrorCode.INVALID_REQUEST, e.getMessage(), null));
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	ResponseEntity<ErrorResponse> handleMediaType(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
			.body(ErrorResponseWriter.body(request, ErrorCode.INVALID_REQUEST, e.getMessage(), null));
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ErrorResponse> handleUploadSize(MaxUploadSizeExceededException e, HttpServletRequest request) {
		return respond(request, ErrorCode.FILE_TOO_LARGE, "파일은 100MB 이하만 올릴 수 있어요", null);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleUnexpected(Exception e, HttpServletRequest request) {
		log.error("처리하지 못한 오류 {} {}", request.getMethod(), request.getRequestURI(), e);
		return respond(request, ErrorCode.INTERNAL_ERROR, "서버 오류가 났어요", null);
	}

	private static ResponseEntity<ErrorResponse> invalid(HttpServletRequest request, ErrorResponseDetails details) {
		return respond(request, ErrorCode.INVALID_REQUEST, "요청 값이 올바르지 않아요", details);
	}

	private static ResponseEntity<ErrorResponse> respond(HttpServletRequest request, ErrorCode code, String message,
			ErrorResponseDetails details) {
		return ResponseEntity.status(ErrorStatus.of(code)).body(ErrorResponseWriter.body(request, code, message, details));
	}

	private static ErrorResponseDetails field(String name, ReasonEnum reason) {
		return new ErrorResponseDetails().fields(List.of(new com.storesearch.api.model.FieldError(name, reason)));
	}

	private static ReasonEnum reasonOf(FieldError error) {
		Map<String, Object> attrs = error.contains(ConstraintViolation.class)
				? error.unwrap(ConstraintViolation.class).getConstraintDescriptor().getAttributes() : Map.of();
		return reasonOf(error.getCodes(), error.getRejectedValue(), attrs);
	}

	/** 검증 규칙 이름 → reason. Size는 실제 길이로 TOO_LONG/TOO_SHORT를 가른다. */
	static ReasonEnum reasonOf(String[] codes, Object rejected, Map<String, Object> attrs) {
		String rule = codes == null || codes.length == 0 ? "" : codes[codes.length - 1];
		if (REQUIRED.contains(rule)) {
			return ReasonEnum.REQUIRED;
		}
		if ("Size".equals(rule) || "Length".equals(rule)) {
			int length = lengthOf(rejected);
			Object max = attrs == null ? null : attrs.get("max");
			return max instanceof Integer m && length > m ? ReasonEnum.TOO_LONG : ReasonEnum.TOO_SHORT;
		}
		if (FORMAT.contains(rule)) {
			return ReasonEnum.INVALID_FORMAT;
		}
		if (RANGE.contains(rule)) {
			return ReasonEnum.OUT_OF_RANGE;
		}
		return ReasonEnum.INVALID_FORMAT;
	}

	private static int lengthOf(Object value) {
		if (value instanceof CharSequence s) {
			return s.length();
		}
		if (value instanceof Collection<?> c) {
			return c.size();
		}
		if (value instanceof Map<?, ?> m) {
			return m.size();
		}
		return 0;
	}

	/** Jackson 경로 → "bounds.sw.lat", 목록은 "paymentCodes[1]". */
	private static String pathOf(DatabindException e) {
		StringBuilder path = new StringBuilder();
		for (var ref : e.getPath()) {
			if (ref.getPropertyName() != null) {
				if (!path.isEmpty()) {
					path.append('.');
				}
				path.append(ref.getPropertyName());
			}
			else if (ref.getIndex() >= 0) {
				path.append('[').append(ref.getIndex()).append(']');
			}
		}
		return path.toString();
	}

}
