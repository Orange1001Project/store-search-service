package com.storesearch.store.service;

/**
 * 임베딩 원문(store_snapshot.doc_text). AI 레포 store_corpus의 search_text_t1_minimal과 같은 모양이어야 한다.
 * 모델이 이 모양으로 학습·평가됐다.
 *
 * <p>"가맹점명: {상호} / 취급품목: {품목}". 품목이 없으면 "가맹점명: {상호}". 300자를 넘으면 품목 쪽을 자른다.
 */
public final class DocText {

	public static final int MAX_LENGTH = 300;

	private static final String NAME = "가맹점명: ";

	private static final String ITEMS = " / 취급품목: ";

	private DocText() {
	}

	public static String of(String storeName, String items) {
		String head = NAME + storeName;
		if (head.length() >= MAX_LENGTH) {
			return head.substring(0, MAX_LENGTH);
		}
		if (items == null || items.isBlank()) {
			return head;
		}
		String full = head + ITEMS + items;
		if (full.length() <= MAX_LENGTH) {
			return full;
		}
		String cut = full.substring(0, MAX_LENGTH);
		// 서로게이트 쌍 가운데서 자르지 않는다
		return Character.isHighSurrogate(cut.charAt(cut.length() - 1)) ? cut.substring(0, cut.length() - 1) : cut;
	}

}
