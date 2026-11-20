package com.storesearch.common;

import com.storesearch.api.model.ErrorCode;
import com.storesearch.api.model.LatLng;
import com.storesearch.api.model.LifecycleStatus;
import com.storesearch.common.error.ApiException;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 공통 모듈 테스트 전용 API. 실제 컨트롤러처럼 /api/v1이 앞에 붙는다. */
@RestController
class CommonTestController {

	@GetMapping("/test/not-found")
	Map<String, String> notFound() {
		throw new ApiException(ErrorCode.STORE_NOT_FOUND, "가게가 없어요");
	}

	@GetMapping("/test/boom")
	Map<String, String> boom() {
		throw new IllegalStateException("예상 못 한 오류");
	}

	@PostMapping("/test/location")
	LatLng location(@Valid @RequestBody LatLng body) {
		return body;
	}

	@GetMapping("/test/status")
	Map<String, String> status(@RequestParam LifecycleStatus status) {
		return Map.of("status", status.name());
	}

	@PostMapping("/auth/test-write")
	Map<String, String> memberWrite() {
		return Map.of("ok", "true");
	}

	/** 로그아웃 접근 규칙 확인용(실제 로그아웃은 로그인 구현 때 만든다). */
	@PostMapping({ "/auth/logout", "/admin/auth/logout" })
	ResponseEntity<Void> logout() {
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/events")
	Map<String, String> events() {
		return Map.of("ok", "true");
	}

}
