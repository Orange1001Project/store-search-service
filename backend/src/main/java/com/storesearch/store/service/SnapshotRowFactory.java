package com.storesearch.store.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 가게 원천 값 → 검색 스냅샷 행. 스냅샷 값 계산은 여기 한 곳에서만 한다(DB_TABLES.md 9절 "유효 값 계산을 서비스 한 곳에서만").
 *
 * <ul>
 *   <li>업종: 원천 온누리중분류(official) → 없으면 산업분류 매핑(ksic) → 그래도 없으면 K010 기타(ksic)
 *   <li>세부 업종: 산업분류 세세분류명(미분류 00000이면 없음)
 *   <li>검색 대상: 영업중 · 숨김 아님 · 좌표 오류 아님(정책 10-5)
 * </ul>
 */
@Component
public class SnapshotRowFactory {

	/** 공식 업종 17개(category 표, seed/category/taxonomy_v1.yaml) */
	private static final Pattern CATEGORY_CODE = Pattern.compile("K0(0[1-9]|1[0-7])");

	static final String FALLBACK_CATEGORY = "K010";

	private static final int SUBCATEGORY_MAX = 40;

	private final KsicCategoryMap ksicMap;

	public SnapshotRowFactory(KsicCategoryMap ksicMap) {
		this.ksicMap = ksicMap;
	}

	public SnapshotRow build(UUID storeId, StoreSource s, String lifecycleStatus, boolean hidden) {
		Optional<KsicCategoryMap.Entry> ksic = ksicMap.find(s.ksicCode());

		String category;
		String source;
		if (s.onnuriCategory() != null && CATEGORY_CODE.matcher(s.onnuriCategory()).matches()) {
			category = s.onnuriCategory();
			source = "official";
		}
		else {
			category = ksic.map(KsicCategoryMap.Entry::onnuriCode)
					.filter(c -> CATEGORY_CODE.matcher(c).matches())
					.orElse(FALLBACK_CATEGORY);
			source = "ksic";
		}
		String subcategory = ksic.filter(e -> !KsicCategoryMap.UNCLASSIFIED.equals(s.ksicCode()))
				.map(KsicCategoryMap.Entry::subclassName)
				.filter(n -> !n.isBlank())
				.map(n -> n.length() > SUBCATEGORY_MAX ? n.substring(0, SUBCATEGORY_MAX) : n)
				.orElse(null);

		List<String> payments = new ArrayList<>(2);
		if (Boolean.TRUE.equals(s.cardPayment())) {
			payments.add("card");
		}
		if (Boolean.TRUE.equals(s.qrPayment())) {
			payments.add("qr");
		}

		boolean searchable = "active".equals(lifecycleStatus) && !hidden && !s.coordError();
		double lat = s.coordError() || s.latitude() == null ? 0 : s.latitude();
		double lng = s.coordError() || s.longitude() == null ? 0 : s.longitude();

		return new SnapshotRow(storeId, s.storeName(), s.items(), s.address(), RoadAddress.parse(s.address()).shortForm(),
				s.legalDongCode() == null ? "" : s.legalDongCode(), s.marketType(), s.marketName(), lat, lng,
				List.of(category), category, source, subcategory, List.copyOf(payments), searchable,
				DocText.of(s.storeName(), s.items()));
	}

}
