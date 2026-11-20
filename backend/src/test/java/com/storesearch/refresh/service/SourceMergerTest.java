package com.storesearch.refresh.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.storesearch.refresh.source.SourceRow;
import com.storesearch.store.service.StoreSource;
import java.util.List;
import org.junit.jupiter.api.Test;

class SourceMergerTest {

	static SourceRow row(int n, String address, Double lat, Double lng, String items, Boolean card, Boolean qr) {
		return new SourceRow(n, "자매소품", "1234567890", null, address, lat, lng, "4812510100", "전통시장", "진해중앙시장", items,
				"47599", "K003", card, qr);
	}

	@Test
	void sameFingerprintRowsBecomeOneStore() {
		List<SourceRow> rows = List.of(
				row(2, "경상남도 창원시 진해구 벚꽃로60번길 12(송학동)", 35.15, 128.69, "소품/잡화", false, null),
				row(3, "경상남도 창원시 진해구 벚꽃로60번길 12(송학동) 1층 자매소품", 35.15001, 128.69, "잡화, 액세서리", true, false),
				row(4, "경상남도 창원시 진해구 벚꽃로60번길 9 (송학동)", 35.15, 128.69, "소품", true, true));

		SourceMerger.Result result = SourceMerger.merge(rows);

		assertThat(result.stores()).hasSize(2);                  // 건물번호 9는 다른 가게
		assertThat(result.mergedStores()).isEqualTo(1);
		assertThat(result.mergedRows()).isEqualTo(2);
		StoreSource merged = result.stores().get(0);
		assertThat(merged.address()).endsWith("1층 자매소품");      // 주소가 가장 긴 행이 대표
		assertThat(merged.latitude()).isEqualTo(35.15001);
		assertThat(merged.items()).isEqualTo("소품, 잡화, 액세서리");   // 순서대로 중복 없이
		assertThat(merged.cardPayment()).isTrue();               // 하나라도 Y면 Y
		assertThat(merged.qrPayment()).isFalse();                // Y 없고 N 있음
		assertThat(merged.sourceConflict()).isTrue();
		assertThat(merged.coordError()).isFalse();               // 1m 차이
	}

	@Test
	void representativePrefersValidCoordinates() {
		StoreSource s = SourceMerger.mergeGroup("fp", List.of(
				row(2, "서울 중구 을지로 1 아주 긴 상세 주소 1층 101호", 0.0, 0.0, null, null, null),
				row(3, "서울 중구 을지로 1", 37.5, 127.0, null, null, null)));

		assertThat(s.latitude()).isEqualTo(37.5);
		assertThat(s.coordError()).isFalse();
		assertThat(s.items()).isNull();
		assertThat(s.cardPayment()).isNull();
	}

	@Test
	void coordinateErrors() {
		assertThat(SourceMerger.mergeGroup("fp", List.of(row(2, "서울 중구 을지로 1", 0.0, 0.0, "a", true, true))).coordError())
				.as("0 좌표").isTrue();
		assertThat(SourceMerger.mergeGroup("fp", List.of(row(2, "서울 중구 을지로 1", null, 127.0, "a", true, true))).coordError())
				.as("빈 좌표").isTrue();
		assertThat(SourceMerger.mergeGroup("fp", List.of(row(2, "서울 중구 을지로 1", 127.0, 37.5, "a", true, true))).coordError())
				.as("위도·경도 바뀜").isTrue();
		assertThat(SourceMerger.mergeGroup("fp", List.of(
				row(2, "서울 중구 을지로 1", 37.5, 127.0, "a", true, true),
				row(3, "서울 중구 을지로 1", 37.502, 127.0, "a", true, true))).coordError())
				.as("같은 가게 행끼리 약 220m").isTrue();
	}

	@Test
	void itemTokensFollowAiRepoRules() {
		assertThat(ItemTokens.parse("한식(국밥, 수육)/분식 ; 주류|기타")).containsExactly("한식(국밥, 수육)", "분식", "주류", "기타");
		assertThat(ItemTokens.parse(" - ")).isEmpty();
		assertThat(ItemTokens.parse("없음")).isEmpty();
		assertThat(ItemTokens.parse("커피&amp;차")).containsExactly("커피&차");
		assertThat(ItemTokens.parse(null)).isEmpty();
	}

}
