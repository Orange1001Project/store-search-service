package com.storesearch.embedding;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 임베딩 서버 설정(application.yml app.embedding).
 *
 * @param url           임베딩 서버 주소(infra/.env EMBEDDING_URL)
 * @param searchTimeout 검색 1건 호출 제한. 넘으면 그 검색은 글자 검색만(FR-FBK-01, 정책 17-6)
 * @param batchTimeout  적재·갱신 묶음(최대 64개) 호출 제한
 * @param connectTimeout 연결 제한
 */
@ConfigurationProperties("app.embedding")
public record EmbeddingProperties(
		@DefaultValue("http://127.0.0.1:8001") String url,
		@DefaultValue("150ms") Duration searchTimeout,
		@DefaultValue("120s") Duration batchTimeout,
		@DefaultValue("1s") Duration connectTimeout) {
}
