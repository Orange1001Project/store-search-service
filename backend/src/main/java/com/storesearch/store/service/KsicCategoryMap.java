package com.storesearch.store.service;

import com.storesearch.common.text.Csv;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 산업분류(KSIC 5자리) → 온누리 중분류·세세분류명. 원천에 업종(온누리중분류)이 비었을 때 쓴다.
 * 파일: seed/category/ksic_onnuri_v1.csv(빌드 때 classpath:seed/로 들어감).
 */
@Component
public class KsicCategoryMap {

	static final String RESOURCE = "seed/category/ksic_onnuri_v1.csv";

	/** 미분류 코드. 세부 업종 이름을 붙이지 않는다 */
	static final String UNCLASSIFIED = "00000";

	public record Entry(String onnuriCode, String subclassName) {
	}

	private final Map<String, Entry> byCode;

	public KsicCategoryMap() {
		this(load());
	}

	KsicCategoryMap(Map<String, Entry> byCode) {
		this.byCode = Map.copyOf(byCode);
	}

	public Optional<Entry> find(String ksicCode) {
		return ksicCode == null ? Optional.empty() : Optional.ofNullable(byCode.get(ksicCode));
	}

	public int size() {
		return byCode.size();
	}

	private static Map<String, Entry> load() {
		Map<String, Entry> map = new HashMap<>();
		try (Reader reader = new InputStreamReader(new ClassPathResource(RESOURCE).getInputStream(), StandardCharsets.UTF_8)) {
			boolean[] header = {true};
			Csv.read(reader, (List<String> row) -> {
				if (header[0]) {
					header[0] = false;
					if (!row.get(0).equals("ksic_code") || !row.get(1).equals("onnuri_code") || !row.get(3).equals("subclass_name")) {
						throw new IllegalStateException(RESOURCE + " 머리글이 예상과 다릅니다: " + row);
					}
					return;
				}
				if (row.size() < 4 || row.get(0).isBlank()) {
					return;
				}
				map.put(row.get(0), new Entry(row.get(1), row.get(3)));
			});
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return map;
	}

}
