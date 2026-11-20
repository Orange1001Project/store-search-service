package com.storesearch.refresh.service;

import com.storesearch.common.text.SourceText;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 취급품목을 낱말 목록으로. AI 레포 items.parse_item_tokens와 같은 규칙(괄호 밖의 , ; / | 로 나눔,
 * "-"·"."·"없음"은 빈 값). 임베딩 원문이 이 규칙으로 만들어져 평가됐다.
 */
final class ItemTokens {

	private static final Set<String> PLACEHOLDERS = Set.of("-", ".", "없음");

	private ItemTokens() {
	}

	static List<String> parse(String raw) {
		String text = SourceText.clean(raw);
		if (text == null || PLACEHOLDERS.contains(text)) {
			return List.of();
		}
		List<String> tokens = new ArrayList<>();
		StringBuilder buffer = new StringBuilder();
		int depth = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '(') {
				depth++;
			}
			else if (c == ')' && depth > 0) {
				depth--;
			}
			if ((c == ',' || c == ';' || c == '/' || c == '|') && depth == 0) {
				add(tokens, buffer);
			}
			else {
				buffer.append(c);
			}
		}
		add(tokens, buffer);
		return tokens;
	}

	private static void add(List<String> tokens, StringBuilder buffer) {
		String token = buffer.toString().strip();
		if (!token.isEmpty()) {
			tokens.add(token);
		}
		buffer.setLength(0);
	}

}
