package com.storesearch.refresh.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storesearch.refresh.TestXlsx;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceFileReaderTest {

	@TempDir
	Path dir;

	@Test
	void readsNovemberColumnsAndCleansValues() {
		Path file = TestXlsx.write(dir.resolve("nov.xlsx"), TestXlsx.NOVEMBER_HEADER, List.of(
				Arrays.asList("피양옥  신불당점", "123-45-67890", "[NULL]", "충청남도 천안시 서북구 불당25로 176 (불당동)  ",
						"127.109129157", "36.816234521", 4413310800L, "전통시장", "천안시장", "한식&amp;분식", 1110, "K006", "Y", "n")));

		SourceFileReader.Result result = SourceFileReader.read(file);

		assertThat(result.legacyColumns()).isFalse();
		assertThat(result.problems()).isEmpty();
		SourceRow r = result.rows().get(0);
		assertThat(r.rowNumber()).isEqualTo(2);
		assertThat(r.storeName()).isEqualTo("피양옥 신불당점");
		assertThat(r.businessNo()).isEqualTo("1234567890");
		assertThat(r.merchantNo()).isNull();                       // [NULL]
		assertThat(r.address()).isEqualTo("충청남도 천안시 서북구 불당25로 176 (불당동)");
		assertThat(r.latitude()).isEqualTo(36.816234521);         // Y좌표
		assertThat(r.longitude()).isEqualTo(127.109129157);       // X좌표
		assertThat(r.legalDongCode()).isEqualTo("4413310800");    // 숫자 셀도 지수 표기 없이
		assertThat(r.itemsRaw()).isEqualTo("한식&분식");
		assertThat(r.ksicCode()).isEqualTo("01110");              // 앞자리 0 복원
		assertThat(r.onnuriCategory()).isEqualTo("K006");
		assertThat(r.qrPayment()).isTrue();
		assertThat(r.cardPayment()).isFalse();
	}

	@Test
	void readsSeptemberColumnNames() {
		Path file = TestXlsx.write(dir.resolve("sep.xlsx"), TestXlsx.SEPTEMBER_HEADER, List.of(
				Arrays.asList("가게", "1234567890", "0415575667", "서울 중구 을지로 1", 37.5, 127.0, 1114010100L, "전통시장", "시장", "국수", "Y", "N")));

		SourceFileReader.Result result = SourceFileReader.read(file);

		assertThat(result.legacyColumns()).isTrue();
		SourceRow r = result.rows().get(0);
		assertThat(r.latitude()).isEqualTo(37.5);
		assertThat(r.longitude()).isEqualTo(127.0);
		assertThat(r.cardPayment()).isTrue();
		assertThat(r.qrPayment()).isFalse();                       // 모바일결제여부
		assertThat(r.ksicCode()).isNull();
		assertThat(r.merchantNo()).isEqualTo("0415575667");
	}

	@Test
	void rowsWithoutNameBusinessNoOrAddressAreReportedAndSkipped() {
		Path file = TestXlsx.write(dir.resolve("bad.xlsx"), TestXlsx.NOVEMBER_HEADER, List.of(
				TestXlsx.november("[NULL]", "1234567890", "서울 중구 을지로 1", 127.0, 37.5, "국수", "56111", "K006", "Y", "Y"),
				TestXlsx.november("가게", "", "서울 중구 을지로 1", 127.0, 37.5, "국수", "56111", "K006", "Y", "Y"),
				TestXlsx.november("가게", "1234567890", "서울 중구 을지로 1", "abc", null, "국수", "56111", "K006", "?", "Y")));

		SourceFileReader.Result result = SourceFileReader.read(file);

		assertThat(result.problems()).extracting(SourceFileReader.Problem::rowNumber, SourceFileReader.Problem::column)
				.containsExactly(org.assertj.core.groups.Tuple.tuple(2, "가맹점명"), org.assertj.core.groups.Tuple.tuple(3, "사업자번호"));
		SourceRow kept = result.rows().get(0);
		assertThat(kept.latitude()).isNull();
		assertThat(kept.longitude()).isNull();
		assertThat(kept.qrPayment()).isNull();
	}

	@Test
	void dateAndFormulaCellsReadLikePandas() {
		Path file = TestXlsx.write(dir.resolve("cells.xlsx"), TestXlsx.NOVEMBER_HEADER, List.of(
				TestXlsx.november(null, "1234567890", "서울 중구 을지로 1", 127.0, 37.5, "의류", "47412", "K002", "Y", "Y"),
				TestXlsx.november(null, "1234567890", "서울 중구 을지로 2", 127.0, 37.5, "의류", "47412", "K002", "Y", "Y")));
		// 첫 행 상호 = 날짜 서식 숫자, 둘째 행 상호 = 수식("+더개꿀"이 수식이 된 경우)
		Path withCells = TestXlsx.write(dir.resolve("cells2.xlsx"), TestXlsx.NOVEMBER_HEADER, List.of(
				replaceName(TestXlsx.november(null, "1234567890", "서울 중구 을지로 1", 127.0, 37.5, "의류", "47412", "K002", "Y", "Y"),
						java.time.LocalDateTime.of(2026, 3, 3, 0, 0)),
				replaceName(TestXlsx.november(null, "1234567890", "서울 중구 을지로 2", 127.0, 37.5, "의류", "47412", "K002", "Y", "Y"),
						new TestXlsx.Formula("+더개꿀"))));

		SourceFileReader.Result result = SourceFileReader.read(withCells);

		assertThat(SourceFileReader.read(file).rows()).isEmpty();
		assertThat(result.rows()).extracting(SourceRow::storeName).containsExactly("2026-03-03 00:00:00");
		assertThat(result.problems()).extracting(SourceFileReader.Problem::rowNumber).containsExactly(3);
	}

	private static List<Object> replaceName(List<Object> row, Object name) {
		List<Object> copy = new java.util.ArrayList<>(row);
		copy.set(0, name);
		return copy;
	}

	@Test
	void missingRequiredColumnFails() {
		Path file = TestXlsx.write(dir.resolve("cols.xlsx"), List.of("가맹점명", "사업자번호", "주소"), List.of());

		assertThatThrownBy(() -> SourceFileReader.read(file))
				.isInstanceOf(SourceFileReader.MissingColumnsException.class)
				.hasMessageContaining("Y좌표").hasMessageContaining("카드결제");
	}

}
