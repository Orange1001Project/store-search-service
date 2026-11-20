package com.storesearch.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/** 실제 HTTP 서버(JDK 내장)를 띄워 시간 초과·오류·차단을 확인한다. */
class EmbeddingClientTest {

	HttpServer server;

	AtomicInteger requests = new AtomicInteger();

	AtomicReference<Behavior> behavior = new AtomicReference<>(Behavior.OK);

	EmbeddingClient client;

	enum Behavior { OK, SLOW, ERROR, WRONG_DIMENSION }

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/embed", exchange -> {
			requests.incrementAndGet();
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			int count = body.split("\",\"", -1).length;   // 테스트용으로 대충 센다
			switch (behavior.get()) {
				case SLOW -> sleep(400);
				case ERROR -> {
					exchange.sendResponseHeaders(500, -1);
					exchange.close();
					return;
				}
				default -> {
				}
			}
			int dim = behavior.get() == Behavior.WRONG_DIMENSION ? 3 : EmbeddingClient.DIMENSION;
			String vector = "[" + String.join(",", Collections.nCopies(dim, "0.03125")) + "]";
			String json = "{\"model\":\"test_model\",\"dimension\":" + dim + ",\"vectors\":["
					+ String.join(",", Collections.nCopies(count, vector)) + "]}";
			byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			try {
				exchange.sendResponseHeaders(200, bytes.length);
				exchange.getResponseBody().write(bytes);
			}
			catch (IOException ignored) {
				// 클라이언트가 시간 초과로 먼저 끊은 경우
			}
			exchange.close();
		});
		server.start();
		EmbeddingProperties properties = new EmbeddingProperties("http://127.0.0.1:" + server.getAddress().getPort(),
				Duration.ofMillis(150), Duration.ofSeconds(5), Duration.ofSeconds(1));
		client = new EmbeddingClient(properties, RestClient.builder(), Clock.systemUTC());
	}

	@AfterEach
	void stop() {
		server.stop(0);
	}

	@Test
	void returnsQueryVector() {
		assertThat(client.embedQuery("통닭")).hasValueSatisfying(v -> assertThat(v).hasSize(1024));
	}

	@Test
	void slowServerFallsBackWithinTheTimeout() {
		behavior.set(Behavior.SLOW);
		long started = System.nanoTime();

		assertThat(client.embedQuery("통닭")).isEmpty();

		assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(350));
	}

	@Test
	void wrongDimensionIsTreatedAsFailure() {
		behavior.set(Behavior.WRONG_DIMENSION);
		assertThat(client.embedQuery("통닭")).isEmpty();
	}

	@Test
	void repeatedFailuresStopCallingTheServer() {
		behavior.set(Behavior.ERROR);
		for (int i = 0; i < CircuitBreaker.FAILURE_THRESHOLD; i++) {
			assertThat(client.embedQuery("통닭")).isEmpty();
		}
		int beforeOpen = requests.get();

		for (int i = 0; i < 5; i++) {
			assertThat(client.embedQuery("통닭")).isEmpty();
		}

		assertThat(requests.get()).isEqualTo(beforeOpen);   // 30초 동안 부르지 않음
	}

	@Test
	void batchReturnsModelAndVectors() {
		EmbeddingClient.EmbedResponse response = client.embedBatch(List.of("가맹점명: 가 / 취급품목: 나", "가맹점명: 다"));

		assertThat(response.model()).isEqualTo("test_model");
		assertThat(response.vectors()).hasSize(2);
	}

	@Test
	void batchFailureThrows() {
		behavior.set(Behavior.ERROR);
		assertThatThrownBy(() -> client.embedBatch(List.of("가맹점명: 가"))).isInstanceOf(EmbeddingUnavailableException.class);
		assertThatThrownBy(() -> client.embedBatch(List.of())).isInstanceOf(IllegalArgumentException.class);
	}

	private static void sleep(long ms) {
		try {
			Thread.sleep(ms);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
