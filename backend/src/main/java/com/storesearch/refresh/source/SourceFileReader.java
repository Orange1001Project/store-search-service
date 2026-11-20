package com.storesearch.refresh.source;

import com.storesearch.common.text.SourceText;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.CellType;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.ReadingOptions;
import org.dhatim.fastexcel.reader.Row;

/**
 * 원천 엑셀(첫 시트)을 스트리밍으로 읽는다. 2026-11-13 파일과 그 이전 파일의 열 이름을 모두 받는다(정책 10-1).
 * 가맹점명·사업자번호·주소가 빈 행은 가게로 만들 수 없어 문제 목록에 넣고 건너뛴다.
 */
public final class SourceFileReader {

	/** 필수 열이 빠진 파일 */
	public static class MissingColumnsException extends RuntimeException {

		MissingColumnsException(List<String> missing) {
			super("원천 파일에 필요한 열이 없습니다: " + missing);
		}

	}

	public record Problem(int rowNumber, String column, String reason) {
	}

	public record Result(List<SourceRow> rows, List<Problem> problems, boolean legacyColumns) {
	}

	enum Column {
		STORE_NAME(true, "가맹점명"),
		BUSINESS_NO(true, "사업자번호"),
		MERCHANT_NO(false, "가맹점번호"),
		ADDRESS(true, "주소"),
		LATITUDE(true, "Y좌표", "LAT"),
		LONGITUDE(true, "X좌표", "LOT"),
		LEGAL_DONG_CODE(true, "법정동코드"),
		MARKET_TYPE(true, "시장분류코드"),
		MARKET_NAME(true, "시장명"),
		ITEMS(true, "취급품목"),
		KSIC_CODE(false, "표준산업분류코드"),
		ONNURI_CATEGORY(false, "온누리중분류"),
		CARD_PAYMENT(true, "카드결제", "카드결제여부"),
		QR_PAYMENT(true, "QR결제", "모바일결제여부");

		final boolean required;

		final List<String> headers;

		Column(boolean required, String... headers) {
			this.required = required;
			this.headers = List.of(headers);
		}

	}

	private SourceFileReader() {
	}

	public static Result read(Path file) {
		try (ReadableWorkbook workbook = new ReadableWorkbook(file.toFile(), new ReadingOptions(true, false));
				Stream<Row> stream = workbook.getFirstSheet().openStream()) {
			List<SourceRow> rows = new ArrayList<>();
			List<Problem> problems = new ArrayList<>();
			Map<Column, Integer> index = null;
			boolean legacy = false;
			for (Row row : (Iterable<Row>) stream::iterator) {
				if (index == null) {
					index = headerIndex(row);
					legacy = !index.containsKey(Column.KSIC_CODE);
					continue;
				}
				Map<Column, String> v = new EnumMap<>(Column.class);
				for (Map.Entry<Column, Integer> e : index.entrySet()) {
					v.put(e.getKey(), cellText(row, e.getValue()));
				}
				if (v.values().stream().allMatch(x -> x == null)) {
					continue;   // 끝의 빈 줄
				}
				int rowNumber = row.getRowNum();
				String name = SourceText.clean(v.get(Column.STORE_NAME));
				String businessNo = SourceText.digits(v.get(Column.BUSINESS_NO));
				String address = SourceText.clean(v.get(Column.ADDRESS));
				if (name == null || businessNo == null || address == null) {
					String column = name == null ? "가맹점명" : businessNo == null ? "사업자번호" : "주소";
					problems.add(new Problem(rowNumber, column, "REQUIRED"));
					continue;
				}
				rows.add(new SourceRow(rowNumber, name, businessNo, SourceText.digits(v.get(Column.MERCHANT_NO)), address,
						number(v.get(Column.LATITUDE)), number(v.get(Column.LONGITUDE)),
						SourceText.digits(v.get(Column.LEGAL_DONG_CODE)), SourceText.clean(v.get(Column.MARKET_TYPE)),
						SourceText.clean(v.get(Column.MARKET_NAME)), SourceText.clean(v.get(Column.ITEMS)),
						ksic(v.get(Column.KSIC_CODE)), SourceText.clean(v.get(Column.ONNURI_CATEGORY)),
						yesNo(v.get(Column.CARD_PAYMENT)), yesNo(v.get(Column.QR_PAYMENT))));
			}
			if (index == null) {
				throw new MissingColumnsException(List.of("(머리글 행)"));
			}
			return new Result(rows, problems, legacy);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Map<Column, Integer> headerIndex(Row header) {
		Map<String, Integer> byName = new java.util.HashMap<>();
		for (int i = 0; i < header.getCellCount(); i++) {
			String name = SourceText.clean(cellText(header, i));
			if (name != null) {
				byName.putIfAbsent(name, i);
			}
		}
		Map<Column, Integer> index = new EnumMap<>(Column.class);
		List<String> missing = new ArrayList<>();
		for (Column column : Column.values()) {
			Optional<Integer> found = column.headers.stream().map(byName::get).filter(i -> i != null).findFirst();
			if (found.isPresent()) {
				index.put(column, found.get());
			}
			else if (column.required) {
				missing.add(column.headers.get(0));
			}
		}
		if (!missing.isEmpty()) {
			throw new MissingColumnsException(missing);
		}
		return index;
	}

	/**
	 * 셀 값을 문자열로. AI 레포(pandas)가 읽은 값과 같게 한다.
	 * <ul>
	 *   <li>숫자: 지수 표기 없이(4413310800)
	 *   <li>날짜 서식 숫자: "2026-03-03 00:00:00"(엑셀이 상호를 날짜로 바꾼 셀)
	 *   <li>오류 값(#NAME? 등, 엑셀이 "+상호"를 수식으로 바꾼 셀)·[NULL]·빈 셀: null
	 * </ul>
	 */
	static String cellText(Row row, int i) {
		if (i >= row.getCellCount()) {
			return null;
		}
		Cell cell = row.getCell(i);
		if (cell == null || cell.getType() == CellType.EMPTY || cell.getType() == CellType.ERROR) {
			return null;
		}
		String raw = cell.getRawValue();
		if (raw == null) {
			return null;
		}
		if (cell.getType() == CellType.NUMBER) {
			if (isDateFormat(cell.getDataFormatId(), cell.getDataFormatString())) {
				return DATE_TIME.format(cell.asDate());
			}
			try {
				raw = new BigDecimal(raw).stripTrailingZeros().toPlainString();
			}
			catch (NumberFormatException ignored) {
				// 그대로 둔다
			}
		}
		else if (cell.getType() == CellType.FORMULA) {
			if (raw.startsWith("#")) {
				return null;   // 수식 결과가 오류
			}
		}
		else if (cell.getType() != CellType.STRING) {
			raw = cell.getText();
		}
		String s = raw.strip();
		return s.isEmpty() || s.equalsIgnoreCase("[NULL]") ? null : s;
	}

	private static final java.time.format.DateTimeFormatter DATE_TIME =
			java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private static final java.util.regex.Pattern QUOTED_OR_BRACKETED = java.util.regex.Pattern.compile("\"[^\"]*\"|\\[[^]]*]");

	/** 엑셀 기본 날짜 서식(14~22, 45~47) 또는 y·m·d·h·s가 들어간 사용자 서식 */
	static boolean isDateFormat(Integer id, String format) {
		if (id != null && ((id >= 14 && id <= 22) || (id >= 45 && id <= 47))) {
			return true;
		}
		if (format == null) {
			return false;
		}
		String f = QUOTED_OR_BRACKETED.matcher(format).replaceAll("").toLowerCase(java.util.Locale.ROOT);
		return !f.equals("general") && f.matches(".*[ymdhs].*");
	}

	static Double number(String value) {
		String s = SourceText.clean(value);
		if (s == null) {
			return null;
		}
		try {
			double d = Double.parseDouble(s);
			return Double.isFinite(d) ? d : null;
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	static Boolean yesNo(String value) {
		String s = SourceText.clean(value);
		if (s == null) {
			return null;
		}
		if (s.equalsIgnoreCase("Y")) {
			return true;
		}
		if (s.equalsIgnoreCase("N")) {
			return false;
		}
		return null;
	}

	/** 숫자로 저장돼 앞자리 0이 빠진 코드(1110 → 01110)를 5자리로 되돌린다. */
	static String ksic(String value) {
		String d = SourceText.digits(value);
		if (d == null || d.length() > 5) {
			return d;
		}
		return "0".repeat(5 - d.length()) + d;
	}

}
