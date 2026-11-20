package com.storesearch.refresh;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;

/** 테스트용 원천 엑셀. 숫자 값은 숫자 셀로, 나머지는 문자열 셀로 쓴다. */
public final class TestXlsx {

	public static final List<String> NOVEMBER_HEADER = List.of("가맹점명", "사업자번호", "가맹점번호", "주소", "X좌표", "Y좌표",
			"법정동코드", "시장분류코드", "시장명", "취급품목", "표준산업분류코드", "온누리중분류", "QR결제", "카드결제");

	public static final List<String> SEPTEMBER_HEADER = List.of("가맹점명", "사업자번호", "가맹점번호", "주소", "LAT", "LOT",
			"법정동코드", "시장분류코드", "시장명", "취급품목", "카드결제여부", "모바일결제여부");

	/** 수식 셀(계산 결과 없이 저장됨) */
	public record Formula(String expression) {
	}

	private TestXlsx() {
	}

	public static Path write(Path file, List<String> header, List<List<Object>> rows) {
		try (OutputStream out = Files.newOutputStream(file)) {
			Workbook wb = new Workbook(out, "test", "1.0");
			Worksheet ws = wb.newWorksheet("Sheet1");
			for (int c = 0; c < header.size(); c++) {
				ws.value(0, c, header.get(c));
			}
			for (int r = 0; r < rows.size(); r++) {
				List<Object> row = rows.get(r);
				for (int c = 0; c < row.size(); c++) {
					Object v = row.get(c);
					if (v instanceof java.time.LocalDateTime d) {
						ws.value(r + 1, c, d);
						ws.style(r + 1, c).format("d-mmm").set();   // 엑셀이 상호를 날짜로 바꾼 셀
					}
					else if (v instanceof Formula f) {
						ws.formula(r + 1, c, f.expression());
					}
					else if (v instanceof Number n) {
						ws.value(r + 1, c, n);
					}
					else if (v != null) {
						ws.value(r + 1, c, v.toString());
					}
				}
			}
			wb.finish();
			return file;
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** 11월 형식 한 행 */
	public static List<Object> november(String name, String businessNo, String address, Object lng, Object lat, String items,
			String ksic, String onnuri, String qr, String card) {
		return java.util.Arrays.asList(name, businessNo, "[NULL]", address, lng, lat, 4413310800L, "전통시장", "테스트시장", items, ksic,
				onnuri, qr, card);
	}

}
