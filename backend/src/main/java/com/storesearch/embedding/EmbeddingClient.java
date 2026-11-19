package com.storesearch.embedding;

import java.net.http.HttpClient;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 임베딩 서버(IR-10) 호출.
 * <ul>
 * <li>{@link #embedQuery}: 검색 1건. 150ms 넘거나 실패하면 빈 값 → 호출한 쪽이 글자 검색만으로 처리(폴백). 실패가 이어지면 30초 차단</li>
 * <li>{@link #embedBatch}: 적재·갱신. 기다려 주고, 실패하면 예외(작업이 멈춰야 하므로)</li>
 * </ul>
 */
@Component
public class EmbeddingClient {

	public static final int DIMENSION = 1024;

	public static final int MAX_BATCH = 64;

	private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);

	private final RestClient searchClient;

	private final RestClient batchClient;

	private final CircuitBreaker breaker;

	public EmbeddingClient(EmbeddingProperties properties, RestClient.Builder builder, Clock clock) {
		HttpClient http = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
		this.searchClient = builder.clone()
			.baseUrl(properties.url())
			.requestFactory(factory(http, properties.searchTimeout()))
			.build();
		this.batchClient = builder.clone()
			.baseUrl(properties.url())
			.requestFactory(factory(http, properties.batchTimeout()))
			.build();
		this.breaker = new CircuitBreaker(clock);
	}

	/** 검색어 벡터. 서버가 느리거나 멈췄으면 빈 값(그 검색은 글자 검색만). */
	public Optional<float[]> embedQuery(String text) {
		if (!breaker.allowCall()) {
			return Optional.empty();
		}
		try {
			EmbedResponse response = call(searchClient, List.of(text));
			breaker.recordSuccess();
			return Optional.of(response.vectors().getFirst());
		}
		catch (RuntimeException e) {
			breaker.recordFailure();
			log.warn("임베딩 호출 실패 — 글자 검색만 사용 ({}{})", e.getClass().getSimpleName(),
					breaker.isOpen() ? ", 30초 차단" : "");
			return Optional.empty();
		}
	}

	/** 가게 문서 벡터 묶음(최대 64개). 실패하면 {@link EmbeddingUnavailableException}. */
	public EmbedResponse embedBatch(List<String> texts) {
		if (texts.isEmpty() || texts.size() > MAX_BATCH) {
			throw new IllegalArgumentException("묶음은 1~" + MAX_BATCH + "개: " + texts.size());
		}
		try {
			return call(batchClient, texts);
		}
		catch (RuntimeException e) {
			throw new EmbeddingUnavailableException("임베딩 묶음 호출 실패", e);
		}
	}

	private static EmbedResponse call(RestClient client, List<String> texts) {
		EmbedResponse response = client.post()
			.uri("/embed")
			.contentType(MediaType.APPLICATION_JSON)
			.body(new EmbedRequest(texts))
			.retrieve()
			.body(EmbedResponse.class);
		if (response == null || response.vectors() == null || response.vectors().size() != texts.size()) {
			throw new RestClientException("응답 벡터 수가 요청과 달라요");
		}
		for (float[] v : response.vectors()) {
			if (v.length != DIMENSION) {
				throw new RestClientException("벡터 차원이 " + DIMENSION + "이 아니에요: " + v.length);
			}
		}
		return response;
	}

	private static JdkClientHttpRequestFactory factory(HttpClient http, java.time.Duration readTimeout) {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
		factory.setReadTimeout(readTimeout);
		return factory;
	}

	record EmbedRequest(List<String> texts) {
	}

	/** @param model 모델 버전(data_version.embedding_model에 남긴다) */
	public record EmbedResponse(String model, int dimension, List<float[]> vectors) {
	}

}
