package com.storesearch.store.service;

import java.util.List;
import java.util.UUID;

/**
 * store_snapshot 한 행(벡터 제외). 원천 값에 관리자 수정을 덮어쓴 유효 값이다(지금은 수정 없음).
 *
 * @param latitude 좌표 오류면 0(검색 대상이 아니라 쓰이지 않음)
 */
public record SnapshotRow(
		UUID storeId,
		String storeName,
		String items,
		String address,
		String addressShort,
		String legalDongCode,
		String marketType,
		String marketName,
		double latitude,
		double longitude,
		List<String> categoryCodes,
		String primaryCategory,
		String categorySource,
		String subcategory,
		List<String> paymentCodes,
		boolean searchable,
		String docText) {
}
