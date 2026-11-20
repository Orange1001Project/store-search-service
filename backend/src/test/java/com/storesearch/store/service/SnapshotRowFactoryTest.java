package com.storesearch.store.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SnapshotRowFactoryTest {

	SnapshotRowFactory factory = new SnapshotRowFactory(new KsicCategoryMap(Map.of(
			"56111", new KsicCategoryMap.Entry("K006", "한식 일반 음식점업"),
			"00000", new KsicCategoryMap.Entry("K010", "해당사항 없음(미분류)"))));

	UUID id = UUID.fromString("00006639-0645-439e-a170-8d3402f5b48e");

	static StoreSource store(String ksic, String onnuri, Boolean card, Boolean qr, boolean coordError) {
		return new StoreSource("fp", "새여울잔치국수", "1234567890", null, "대전광역시 대덕구 신탄진북로 35 (신탄진동) 1층", 36.45, 127.43,
				"3023011100", "골목형상점가", "신탄진시장", "국수, 잔치국수", ksic, onnuri, card, qr, false, coordError);
	}

	@Test
	void officialCategoryWins() {
		SnapshotRow r = factory.build(id, store("56111", "K007", true, true, false), "active", false);

		assertThat(r.primaryCategory()).isEqualTo("K007");
		assertThat(r.categorySource()).isEqualTo("official");
		assertThat(r.categoryCodes()).containsExactly("K007");
		assertThat(r.subcategory()).isEqualTo("한식 일반 음식점업");
		assertThat(r.paymentCodes()).containsExactly("card", "qr");
		assertThat(r.searchable()).isTrue();
		assertThat(r.addressShort()).isEqualTo("대덕구 신탄진북로");
		assertThat(r.docText()).isEqualTo("가맹점명: 새여울잔치국수 / 취급품목: 국수, 잔치국수");
		assertThat(r.latitude()).isEqualTo(36.45);
	}

	@Test
	void ksicFillsMissingCategoryThenFallsBackToEtc() {
		SnapshotRow byKsic = factory.build(id, store("56111", null, false, null, false), "active", false);
		assertThat(byKsic.primaryCategory()).isEqualTo("K006");
		assertThat(byKsic.categorySource()).isEqualTo("ksic");
		assertThat(byKsic.paymentCodes()).isEmpty();

		SnapshotRow unknown = factory.build(id, store("99999", "X01", null, null, false), "active", false);
		assertThat(unknown.primaryCategory()).isEqualTo("K010");
		assertThat(unknown.subcategory()).isNull();

		SnapshotRow unclassified = factory.build(id, store("00000", "K010", null, null, false), "active", false);
		assertThat(unclassified.subcategory()).as("미분류는 세부 업종 없음").isNull();
	}

	@Test
	void notSearchableWhenCoordErrorHiddenOrNotActive() {
		SnapshotRow coord = factory.build(id, store("56111", "K006", true, true, true), "active", false);
		assertThat(coord.searchable()).isFalse();
		assertThat(coord.latitude()).isZero();
		assertThat(factory.build(id, store("56111", "K006", true, true, false), "active", true).searchable()).isFalse();
		assertThat(factory.build(id, store("56111", "K006", true, true, false), "pending", false).searchable()).isFalse();
	}

	@Test
	void docTextCutsItemsAt300Characters() {
		assertThat(DocText.of("가게", null)).isEqualTo("가맹점명: 가게");
		assertThat(DocText.of("가게", " ")).isEqualTo("가맹점명: 가게");
		String longItems = "품".repeat(400);
		String text = DocText.of("가게", longItems);
		assertThat(text).hasSize(300).startsWith("가맹점명: 가게 / 취급품목: 품품");
	}

	@Test
	void loadsSeedFile() {
		KsicCategoryMap map = new KsicCategoryMap();
		assertThat(map.size()).isGreaterThan(1000);
		assertThat(map.find("56111")).get().extracting(KsicCategoryMap.Entry::onnuriCode).isEqualTo("K006");
	}

}
