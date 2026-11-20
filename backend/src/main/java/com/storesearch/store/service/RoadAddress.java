package com.storesearch.store.service;

import com.storesearch.common.text.SourceText;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 주소를 시도 · (시군구 · 도로명) · 번호 · 상세로 나눈다. 지문의 도로명 키와 결과 카드의 짧은 주소가 같은 규칙을 쓴다.
 *
 * <p>괄호 앞까지에서 시도 이름을 빼고, 처음 나오는 번호(건물번호 30, 28-19, 지하60, 지번 203-58)까지만 본다.
 * 번호가 도로명에 붙어 오면(중앙대로767, 마장로31길40) 나눠서 본다. 번호가 없으면 시도를 뺀 나머지 전체.
 */
public final class RoadAddress {

	private static final Set<String> SIDO = Set.of(
			"서울특별시", "서울", "부산광역시", "부산", "대구광역시", "대구", "인천광역시", "인천",
			"광주광역시", "광주", "대전광역시", "대전", "울산광역시", "울산", "세종특별자치시", "세종",
			"경기도", "경기", "강원특별자치도", "강원도", "강원", "충청북도", "충북", "충청남도", "충남",
			"전북특별자치도", "전라북도", "전북", "전라남도", "전남", "경상북도", "경북", "경상남도", "경남",
			"제주특별자치도", "제주도", "제주");

	private static final Pattern NUMBER = Pattern.compile("(지하)?\\d+(-\\d+)?");

	private static final Pattern ROAD_WITH_NUMBER = Pattern.compile("(.+[로길])((지하)?\\d+(-\\d+)?)");

	private static final Pattern SPACES = Pattern.compile("\\s+");

	private final List<String> tokens;

	/** 번호 토큰 위치. 없으면 -1 */
	private final int numberIndex;

	private RoadAddress(List<String> tokens, int numberIndex) {
		this.tokens = tokens;
		this.numberIndex = numberIndex;
	}

	public static RoadAddress parse(String address) {
		String s = SourceText.clean(address);
		if (s == null) {
			return new RoadAddress(List.of(), -1);
		}
		int paren = s.indexOf('(');
		if (paren >= 0) {
			s = s.substring(0, paren);
		}
		List<String> tokens = new ArrayList<>();
		for (String t : SPACES.split(s.replace(',', ' ').strip())) {
			if (t.isEmpty()) {
				continue;
			}
			Matcher m = ROAD_WITH_NUMBER.matcher(t);
			if (m.matches()) {
				tokens.add(m.group(1));
				tokens.add(m.group(2));
			}
			else {
				tokens.add(t);
			}
		}
		if (!tokens.isEmpty() && SIDO.contains(tokens.get(0))) {
			tokens.remove(0);
		}
		for (int i = 1; i < tokens.size(); i++) {
			if (NUMBER.matcher(tokens.get(i)).matches()) {
				return new RoadAddress(List.copyOf(tokens.subList(0, i + 1)), i);
			}
		}
		return new RoadAddress(List.copyOf(tokens), -1);
	}

	/** 지문용 키: 번호까지 공백 없이. 예: 충주시무학1길30 */
	public String key() {
		return String.join("", tokens);
	}

	/** 결과 카드용 시군구 + 도로명(번호 앞까지). 예: 충주시 무학1길. 비면 null */
	public String shortForm() {
		List<String> head = numberIndex > 0 ? tokens.subList(0, numberIndex) : tokens;
		String s = String.join(" ", head);
		return s.isEmpty() ? null : s;
	}

}
