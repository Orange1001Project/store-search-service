package com.storesearch.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextTest {

	@Test
	void cleanMatchesAiRepoNormalization() {
		assertThat(SourceText.clean("  가게&nbsp;이름  <br/> 2호점 ")).isEqualTo("가게 이름 2호점");
		assertThat(SourceText.clean("A&amp;B &#44032; &#xAC00; &unknown;")).isEqualTo("A&B 가 가 &unknown;");
		assertThat(SourceText.clean("대박플라워-&gt；펫플라워")).as("세미콜론 없는 엔티티(9월 원천 실제 값)").isEqualTo("대박플라워->；펫플라워");
		assertThat(SourceText.clean("A&ampB &#44032 &#xZZ;")).isEqualTo("A&B 가 &#xZZ;");
		assertThat(SourceText.clean("   ")).isNull();
		assertThat(SourceText.digits("123-45-67890")).isEqualTo("1234567890");
		assertThat(SourceText.digits("-")).isNull();
	}

	@Test
	void csvRoundTripsQuotesCommasAndNewlines() {
		String text = Csv.line("store_id", "doc_text") + Csv.line("a", "가맹점명: \"큰\" 집, 2호 / 취급품목: 국수\n떡") + Csv.line("b", "");
		List<List<String>> rows = new ArrayList<>();

		Csv.read(new StringReader("﻿" + text), rows::add);

		assertThat(rows).containsExactly(List.of("store_id", "doc_text"),
				List.of("a", "가맹점명: \"큰\" 집, 2호 / 취급품목: 국수\n떡"), List.of("b", ""));
	}

}
