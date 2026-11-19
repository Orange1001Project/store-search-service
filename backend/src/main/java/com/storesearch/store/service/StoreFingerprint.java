package com.storesearch.store.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 가게 지문: 원천 파일의 행이 지난 버전의 어느 가게인지 판단한다(store.fingerprint, DB_TABLES.md 3절).
 *
 * <p>지문 = sha256(사업자번호 | 가맹점명 | 도로명 키). 도로명 키는 주소에서 상세 주소를 뺀 부분이다.
 * 상세 주소(층·호·상호)는 매번 다르게 적혀 오므로 보지 않는다. 9월 → 11월 파일에서 전체 주소를 보면
 * 80.7%만 이어졌고, 도로명 키로 보면 89.8%가 이어졌다(2026-11-19 측정).
 */
public final class StoreFingerprint {

	private static final Set<String> SIDO = Set.of(
			"서울특별시", "서울", "부산광역시", "부산", "대구광역시", "대구", "인천광역시", "인천",
			"광주광역시", "광주", "대전광역시", "대전", "울산광역시", "울산", "세종특별자치시", "세종",
			"경기도", "경기", "강원특별자치도", "강원도", "강원", "충청북도", "충북", "충청남도", "충남",
			"전북특별자치도", "전라북도", "전북", "전라남도", "전남", "경상북도", "경북", "경상남도", "경남",
			"제주특별자치도", "제주도", "제주");

	/** 건물번호·지번: 30, 28-19, 지하60 */
	private static final Pattern NUMBER = Pattern.compile("(지하)?\\d+(-\\d+)?");

	/** 도로명에 번호가 붙어 온 경우: 중앙대로767, 마장로31길40 */
	private static final Pattern ROAD_WITH_NUMBER = Pattern.compile("(.+[로길])((지하)?\\d+(-\\d+)?)");

	private static final Pattern HTML_BREAK = Pattern.compile("(?i)<br\\s*/?>");

	private static final Pattern SPACES = Pattern.compile("\\s+");

	private StoreFingerprint() {
	}

	public static String of(String businessNo, String storeName, String address) {
		String key = digits(businessNo) + "|" + clean(storeName) + "|" + roadKey(address);
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * 괄호 앞까지에서 시도를 빼고, 처음 나오는 번호(건물번호·지번)까지 공백 없이 잇는다.
	 * 번호가 없으면 시도를 뺀 나머지 전체.
	 * 예: "충청북도 충주시 무학1길 30 (봉방동) 김창숙미용실" → "충주시무학1길30"
	 */
	static String roadKey(String address) {
		String s = clean(address);
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
				return String.join("", tokens.subList(0, i + 1));
			}
		}
		return String.join("", tokens);
	}

	/** HTML 줄바꿈·&nbsp;·연속 공백 정리(AI 레포 normalize_spaces·clean_address와 같은 처리). */
	static String clean(String value) {
		if (value == null) {
			return "";
		}
		String s = HTML_BREAK.matcher(value).replaceAll(" ")
				.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
				.replace("&quot;", "\"").replace("&#39;", "'")
				.replace(' ', ' ');
		return SPACES.matcher(s).replaceAll(" ").strip();
	}

	private static String digits(String value) {
		return value == null ? "" : value.replaceAll("\\D", "");
	}

}
