package com.storesearch.refresh.service;

import com.storesearch.refresh.source.SourceRow;
import com.storesearch.store.service.StoreFingerprint;
import com.storesearch.store.service.StoreSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 같은 지문의 원천 행을 가게 하나로 합친다(DB_TABLES.md 3절, 가정 A2).
 *
 * <ul>
 *   <li>대표 행 = 좌표가 국내 범위 안인 행 → 주소가 가장 긴 행 → 파일에서 먼저 나온 행. 상호·주소·좌표·시장·업종은 대표 행 값
 *   <li>품목 = 행들의 품목 낱말을 중복 없이 순서대로. 결제 = 하나라도 Y면 Y
 *   <li>좌표·품목·결제가 행끼리 다르면 source_conflict
 *   <li>좌표 오류 = 대표 좌표가 비었거나 국내 범위 밖(0 포함), 또는 행끼리 100m 넘게 다름(정책 10-4 "충돌")
 * </ul>
 */
final class SourceMerger {

	/** 국내 범위(AI 레포 configs/data/default.yaml validation과 같음) */
	static final double LAT_MIN = 33.0;

	static final double LAT_MAX = 39.5;

	static final double LNG_MIN = 124.0;

	static final double LNG_MAX = 132.0;

	static final double CONFLICT_METERS = 100;

	record Result(List<StoreSource> stores, int mergedStores, int mergedRows) {
	}

	private SourceMerger() {
	}

	static Result merge(List<SourceRow> rows) {
		Map<String, List<SourceRow>> groups = new LinkedHashMap<>();
		for (SourceRow row : rows) {
			String fp = StoreFingerprint.of(row.businessNo(), row.storeName(), row.address());
			groups.computeIfAbsent(fp, k -> new ArrayList<>(1)).add(row);
		}
		List<StoreSource> stores = new ArrayList<>(groups.size());
		int mergedStores = 0;
		int mergedRows = 0;
		for (Map.Entry<String, List<SourceRow>> e : groups.entrySet()) {
			List<SourceRow> group = e.getValue();
			if (group.size() > 1) {
				mergedStores++;
				mergedRows += group.size();
			}
			stores.add(mergeGroup(e.getKey(), group));
		}
		return new Result(stores, mergedStores, mergedRows);
	}

	static StoreSource mergeGroup(String fingerprint, List<SourceRow> group) {
		SourceRow rep = group.stream()
				.min(Comparator.comparing((SourceRow r) -> !validCoord(r))
						.thenComparing(r -> -r.address().length())
						.thenComparingInt(SourceRow::rowNumber))
				.orElseThrow();

		Set<String> tokens = new LinkedHashSet<>();
		Set<String> itemVariants = new LinkedHashSet<>();
		Set<String> coordVariants = new LinkedHashSet<>();
		Set<Boolean> cardVariants = new LinkedHashSet<>();
		Set<Boolean> qrVariants = new LinkedHashSet<>();
		for (SourceRow r : group) {
			tokens.addAll(ItemTokens.parse(r.itemsRaw()));
			itemVariants.add(Objects.toString(r.itemsRaw(), ""));
			coordVariants.add(r.latitude() + "," + r.longitude());
			cardVariants.add(r.cardPayment());
			qrVariants.add(r.qrPayment());
		}
		boolean conflict = itemVariants.size() > 1 || coordVariants.size() > 1
				|| cardVariants.size() > 1 || qrVariants.size() > 1;
		boolean coordError = !validCoord(rep) || maxDistanceMeters(group) > CONFLICT_METERS;

		return new StoreSource(fingerprint, rep.storeName(), rep.businessNo(), rep.merchantNo(), rep.address(),
				rep.latitude(), rep.longitude(), rep.legalDongCode(), rep.marketType(), rep.marketName(),
				tokens.isEmpty() ? null : String.join(", ", tokens), rep.ksicCode(), rep.onnuriCategory(),
				anyYes(cardVariants), anyYes(qrVariants), conflict, coordError);
	}

	static boolean validCoord(SourceRow r) {
		return r.latitude() != null && r.longitude() != null
				&& r.latitude() >= LAT_MIN && r.latitude() <= LAT_MAX
				&& r.longitude() >= LNG_MIN && r.longitude() <= LNG_MAX;
	}

	/** 하나라도 Y면 Y, 아니면 하나라도 N이면 N, 모두 비면 null */
	private static Boolean anyYes(Set<Boolean> values) {
		if (values.contains(Boolean.TRUE)) {
			return true;
		}
		return values.contains(Boolean.FALSE) ? false : null;
	}

	/** 국내 범위 안 좌표끼리의 가장 먼 거리(m) */
	static double maxDistanceMeters(List<SourceRow> group) {
		List<SourceRow> valid = group.stream().filter(SourceMerger::validCoord).toList();
		double max = 0;
		for (int i = 0; i < valid.size(); i++) {
			for (int j = i + 1; j < valid.size(); j++) {
				max = Math.max(max, haversine(valid.get(i), valid.get(j)));
			}
		}
		return max;
	}

	private static double haversine(SourceRow a, SourceRow b) {
		double la1 = Math.toRadians(a.latitude());
		double la2 = Math.toRadians(b.latitude());
		double dLat = la2 - la1;
		double dLng = Math.toRadians(b.longitude() - a.longitude());
		double h = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(la1) * Math.cos(la2) * Math.pow(Math.sin(dLng / 2), 2);
		return 2 * 6_371_000 * Math.asin(Math.sqrt(h));
	}

}
