package com.storesearch.store.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 주소 예시는 9월·11월 원천 파일에서 실제로 나온 모양. 사업자번호는 가짜. */
class StoreFingerprintTest {

	static final String BIZ = "1234567890";

	@ParameterizedTest
	@CsvSource(delimiter = '=', value = {
			"충청북도 충주시 무학1길 30 (봉방동) 김창숙미용실 = 충주시무학1길30",
			"부산광역시 부산진구 복지로13번길 73(개금동) 1층 대명횟집 = 부산진구복지로13번길73",
			"경기도 구리시 안골로63번길 28-19 (수택동) 파크상가B동 103호(수택동) = 구리시안골로63번길28-19",
			"충청북도 옥천군 옥천읍 삼금로 45 1층 15호 = 옥천군옥천읍삼금로45",
			"제주특별자치도 제주시 중앙로 지하 60 (일도일동) 201,202호 = 제주시중앙로지하60",
			"경상남도 진주시 진주대로 지하1035(대안동) D-13호 = 진주시진주대로지하1035",
			"울산광역시 중구 새즈믄해거리 41(성남동) 1층 = 중구새즈믄해거리41",
			"경상북도 경주시 황오동 203-58 = 경주시황오동203-58",
			"서울특별시 성동구 마장로31길40 1층 우성식품 = 성동구마장로31길40",
			"부산 부산진구 중앙대로767 1층,부전동 = 부산진구중앙대로767",
			"경상남도 창원시 마산합포구 어시장6길(남성동) 명성상회 = 창원시마산합포구어시장6길",
	})
	void roadKeyDropsSidoAndDetail(String address, String expected) {
		assertThat(StoreFingerprint.roadKey(address)).isEqualTo(expected);
	}

	@Test
	void sameStoreWhenOnlyDetailAddressChanges() {
		assertThat(StoreFingerprint.of(BIZ, "김창숙미용실", "충청북도 충주시 무학1길 30 (봉방동)"))
				.isEqualTo(StoreFingerprint.of(BIZ, "김창숙미용실", "충청북도 충주시 무학1길 30 (봉방동) 김창숙미용실"));
		assertThat(StoreFingerprint.of(BIZ, "가게", "부산광역시 부산진구 중앙대로 767"))
				.isEqualTo(StoreFingerprint.of(BIZ, "가게", "부산 부산진구 중앙대로767 1층,부전동"));
	}

	@Test
	void spacingAndHtmlNoiseDoNotMatter() {
		assertThat(StoreFingerprint.of(BIZ, "피양옥  신불당점", "충청남도 천안시 서북구 불당25로 176 (불당동)  "))
				.isEqualTo(StoreFingerprint.of(BIZ, "피양옥 신불당점", "충청남도  천안시<br>서북구 불당25로 176&nbsp;(불당동)"));
		assertThat(StoreFingerprint.of("123-45-67890", "가게", "서울 중구 을지로 1"))
				.isEqualTo(StoreFingerprint.of(BIZ, "가게", "서울 중구 을지로 1"));
	}

	@Test
	void differentStoreWhenNameBuildingOrBusinessChanges() {
		String base = StoreFingerprint.of(BIZ, "자매소품", "경상남도 창원시 진해구 벚꽃로60번길 12(송학동) 자매소품");

		assertThat(StoreFingerprint.of(BIZ, "자매소품", "경상남도 창원시 진해구 벚꽃로60번길 9 (송학동) 자매소품")).isNotEqualTo(base);
		assertThat(StoreFingerprint.of(BIZ, "자매상회", "경상남도 창원시 진해구 벚꽃로60번길 12(송학동) 자매소품")).isNotEqualTo(base);
		assertThat(StoreFingerprint.of("1234567891", "자매소품", "경상남도 창원시 진해구 벚꽃로60번길 12(송학동)")).isNotEqualTo(base);
	}

	@Test
	void isSha256Hex() {
		assertThat(StoreFingerprint.of(BIZ, "가게", "서울 중구 을지로 1")).matches("[0-9a-f]{64}");
		assertThat(StoreFingerprint.of(null, null, null)).hasSize(64);
	}

}
