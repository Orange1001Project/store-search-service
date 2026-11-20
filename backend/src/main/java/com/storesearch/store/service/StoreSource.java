package com.storesearch.store.service;

/**
 * 가게 한 곳의 원천 값(같은 지문의 원천 행을 합친 결과). store 표의 원천 열과 같다.
 *
 * @param items 품목을 중복 없이 ", "로 이은 것(AI 레포 item_text와 같은 규칙). 없으면 null
 * @param sourceConflict 같은 가게 원천 행끼리 좌표·품목·결제가 다름
 * @param coordError 좌표가 비었거나 0·국내 밖이거나, 같은 가게 원천 행끼리 100m 넘게 다름
 */
public record StoreSource(
		String fingerprint,
		String storeName,
		String businessNo,
		String merchantNo,
		String address,
		Double latitude,
		Double longitude,
		String legalDongCode,
		String marketType,
		String marketName,
		String items,
		String ksicCode,
		String onnuriCategory,
		Boolean cardPayment,
		Boolean qrPayment,
		boolean sourceConflict,
		boolean coordError) {
}
