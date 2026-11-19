package com.storesearch.common.json;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.lang.Nullable;

/**
 * 시각 규칙(API_RULES.md 1절): API에는 KST 오프셋을 붙인 ISO 8601(2026-11-13T14:30:00+09:00), DB에는 UTC(timestamptz).
 * 하루·한 달 경계도 한국 시간 기준이다.
 */
public final class KstTime {

	public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	private KstTime() {
	}

	/** DB에서 읽은 시각을 API 응답용으로. */
	public static @Nullable OffsetDateTime toApi(@Nullable Instant instant) {
		return instant == null ? null : instant.atZone(ZONE).toOffsetDateTime();
	}

	public static @Nullable OffsetDateTime toApi(@Nullable OffsetDateTime time) {
		return time == null ? null : time.atZoneSameInstant(ZONE).toOffsetDateTime();
	}

	/** 한국 날짜(통계·데이터 버전 이름). */
	public static LocalDate today(Clock clock) {
		return LocalDate.now(clock.withZone(ZONE));
	}

}
