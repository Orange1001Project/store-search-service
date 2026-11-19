package com.storesearch.embedding;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;

/**
 * 임베딩 호출 차단기(정책 17-6): 최근 20회 중 실패가 절반(10회) 이상이면 30초 동안 호출하지 않고,
 * 30초 뒤 한 번 시험해서 성공하면 다시 열고 실패하면 또 30초 막는다.
 */
class CircuitBreaker {

	static final int WINDOW = 20;

	static final int FAILURE_THRESHOLD = 10;

	static final Duration OPEN_DURATION = Duration.ofSeconds(30);

	private final Clock clock;

	private final ArrayDeque<Boolean> recent = new ArrayDeque<>(WINDOW);   // true = 실패

	private Instant openUntil = Instant.MIN;

	private boolean trialInFlight;

	CircuitBreaker(Clock clock) {
		this.clock = clock;
	}

	/** 지금 호출해도 되는가. 막힌 시간이 지났으면 시험 호출 하나만 허용한다. */
	synchronized boolean allowCall() {
		if (openUntil.equals(Instant.MIN)) {
			return true;
		}
		if (clock.instant().isBefore(openUntil) || trialInFlight) {
			return false;
		}
		trialInFlight = true;
		return true;
	}

	synchronized void recordSuccess() {
		if (trialInFlight) {
			close();
			return;
		}
		push(false);
	}

	synchronized void recordFailure() {
		if (trialInFlight) {
			trialInFlight = false;
			openUntil = clock.instant().plus(OPEN_DURATION);
			return;
		}
		push(true);
		if (recent.stream().filter(Boolean::booleanValue).count() >= FAILURE_THRESHOLD) {
			openUntil = clock.instant().plus(OPEN_DURATION);
		}
	}

	synchronized boolean isOpen() {
		return !openUntil.equals(Instant.MIN);
	}

	private void push(boolean failed) {
		if (recent.size() == WINDOW) {
			recent.removeFirst();
		}
		recent.addLast(failed);
	}

	private void close() {
		trialInFlight = false;
		openUntil = Instant.MIN;
		recent.clear();
	}

}
