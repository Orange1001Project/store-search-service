package com.storesearch.embedding;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CircuitBreakerTest {

	MutableClock clock = new MutableClock();

	CircuitBreaker breaker = new CircuitBreaker(clock);

	@Test
	void opensAfterHalfOfLastTwentyFail() {
		for (int i = 0; i < 10; i++) {
			breaker.recordSuccess();
		}
		for (int i = 0; i < 9; i++) {
			breaker.recordFailure();
		}
		assertThat(breaker.allowCall()).isTrue();

		breaker.recordFailure();   // 최근 20회 중 10회 실패

		assertThat(breaker.allowCall()).isFalse();
	}

	@Test
	void oldFailuresSlideOutOfTheWindow() {
		for (int i = 0; i < 9; i++) {
			breaker.recordFailure();
		}
		for (int i = 0; i < 20; i++) {
			breaker.recordSuccess();
		}
		breaker.recordFailure();

		assertThat(breaker.allowCall()).isTrue();
	}

	@Test
	void afterThirtySecondsOneTrialDecides() {
		tripOpen();
		clock.advance(Duration.ofSeconds(29));
		assertThat(breaker.allowCall()).isFalse();

		clock.advance(Duration.ofSeconds(1));
		assertThat(breaker.allowCall()).isTrue();    // 시험 호출
		assertThat(breaker.allowCall()).isFalse();   // 시험 중에는 하나만

		breaker.recordFailure();                     // 시험 실패 → 다시 30초
		assertThat(breaker.allowCall()).isFalse();
		clock.advance(Duration.ofSeconds(30));
		assertThat(breaker.allowCall()).isTrue();

		breaker.recordSuccess();                     // 시험 성공 → 닫힘, 기록 초기화
		assertThat(breaker.isOpen()).isFalse();
		breaker.recordFailure();
		assertThat(breaker.allowCall()).isTrue();
	}

	private void tripOpen() {
		for (int i = 0; i < CircuitBreaker.FAILURE_THRESHOLD; i++) {
			breaker.recordFailure();
		}
		assertThat(breaker.isOpen()).isTrue();
	}

	static class MutableClock extends Clock {

		private Instant now = Instant.parse("2026-11-19T00:00:00Z");

		void advance(Duration d) {
			now = now.plus(d);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

	}

}
