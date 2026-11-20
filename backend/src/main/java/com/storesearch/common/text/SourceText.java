package com.storesearch.common.text;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 원천 엑셀 문자열 정리. AI 레포 {@code text_cleaning.normalize_spaces}·{@code clean_address}와 같은 결과를 낸다
 * (HTML 엔티티 복원 → &nbsp; → {@code <br>} → 연속 공백 하나 → 앞뒤 공백 제거). 빈 문자열은 null.
 */
public final class SourceText {

	/** 파이썬 html.unescape와 같은 모양: 세미콜론이 없어도 잡는다 */
	private static final Pattern ENTITY = Pattern.compile("&(#[0-9]+;?|#[xX][0-9a-fA-F]+;?|[^\\t\\n\\f <&#;]{1,32};?)");

	private static final Map<String, String> NAMED = Map.of(
			"amp", "&", "lt", "<", "gt", ">", "quot", "\"", "apos", "'", "nbsp", " ", "middot", "·");

	/** 세미콜론 없이도 풀리는 이름(HTML5 legacy). "&gt；펫" → ">；펫" */
	private static final List<String> LEGACY = List.of("middot", "nbsp", "quot", "amp", "gt", "lt");

	private static final Pattern HTML_BREAK = Pattern.compile("(?i)<\\s*br\\s*/?\\s*>");

	private static final Pattern SPACES = Pattern.compile("\\s+");

	private SourceText() {
	}

	/** 공백·엔티티 정리. 비면 null. */
	public static String clean(String value) {
		if (value == null) {
			return null;
		}
		String s = unescape(value).replace(' ', ' ');
		s = HTML_BREAK.matcher(s).replaceAll(" ");
		s = SPACES.matcher(s).replaceAll(" ").strip();
		return s.isEmpty() ? null : s;
	}

	/** 숫자만 남김. 비면 null(사업자번호·법정동코드 등). */
	public static String digits(String value) {
		String s = clean(value);
		if (s == null) {
			return null;
		}
		String d = s.replaceAll("\\D+", "");
		return d.isEmpty() ? null : d;
	}

	static String unescape(String s) {
		if (s.indexOf('&') < 0) {
			return s;
		}
		Matcher m = ENTITY.matcher(s);
		StringBuilder out = new StringBuilder(s.length());
		while (m.find()) {
			String ref = m.group(1);
			String name = ref.endsWith(";") ? ref.substring(0, ref.length() - 1) : ref;
			String replacement;
			if (name.startsWith("#x") || name.startsWith("#X")) {
				replacement = number(name.substring(2), 16, m.group());
			}
			else if (name.startsWith("#")) {
				replacement = number(name.substring(1), 10, m.group());
			}
			else if (ref.endsWith(";") && NAMED.containsKey(name)) {
				replacement = NAMED.get(name);
			}
			else {
				replacement = m.group();
				for (String legacy : LEGACY) {
					if (ref.startsWith(legacy)) {
						replacement = NAMED.get(legacy) + ref.substring(legacy.length());
						break;
					}
				}
			}
			m.appendReplacement(out, Matcher.quoteReplacement(replacement));
		}
		m.appendTail(out);
		return out.toString();
	}

	private static String number(String digits, int radix, String original) {
		try {
			int cp = Integer.parseInt(digits, radix);
			return Character.isValidCodePoint(cp) ? new String(Character.toChars(cp)) : original;
		}
		catch (NumberFormatException e) {
			return original;
		}
	}

}
