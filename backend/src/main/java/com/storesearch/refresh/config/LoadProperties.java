package com.storesearch.refresh.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 첫 적재 명령 설정(app.load, docs/dev/DATA_LOAD.md). 경로는 명령줄 인자로 준다.
 *
 * @param source          원천 엑셀(11월 파일)
 * @param legacySource    store_id를 이어받을 이전 원천 엑셀(9월 파일). 비우면 모두 새 id
 * @param legacyRegistry  AI 레지스트리 CSV(entity_fingerprint, store_id)
 * @param legacyQrels     평가 정답 CSV(query_id, doc_id, relevance). id가 겹칠 때 정답에 나오는 id를 남긴다
 * @param dir             Colab과 주고받는 파일 폴더
 * @param embeddingModel  data_version.embedding_model. Colab 결과의 model과 같아야 한다
 * @param verifyWithServer 넣기 전에 표본을 로컬 임베딩 서버로 다시 계산해 Colab 벡터와 비교
 * @param verifySample    표본 수(최대 64)
 */
@ConfigurationProperties("app.load")
public record LoadProperties(
		Path source,
		Path legacySource,
		Path legacyRegistry,
		Path legacyQrels,
		@DefaultValue("../data/load") Path dir,
		@DefaultValue("bge_m3_ft_mj_20261014_0743") String embeddingModel,
		@DefaultValue("true") boolean verifyWithServer,
		@DefaultValue("32") int verifySample) {
}
