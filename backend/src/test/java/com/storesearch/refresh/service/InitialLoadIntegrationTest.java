package com.storesearch.refresh.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.storesearch.TestcontainersConfig;
import com.storesearch.refresh.TestXlsx;
import com.storesearch.refresh.source.SourceRow;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 첫 적재 전체: 엑셀 → prepare → (Colab 대신 테스트가 벡터 파일 작성) → import → 활성 버전. */
@SpringBootTest
@Import(TestcontainersConfig.class)
class InitialLoadIntegrationTest {

	static final Path DIR;

	static final UUID SEPTEMBER_ID = UUID.fromString("17c8bdaf-0f26-46d7-8bb4-a614b3e39c75");

	static {
		try {
			DIR = Files.createTempDirectory("initial-load");
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@DynamicPropertySource
	static void load(DynamicPropertyRegistry registry) {
		Path nov = TestXlsx.write(DIR.resolve("nov.xlsx"), TestXlsx.NOVEMBER_HEADER, List.of(
				TestXlsx.november("김창숙미용실", "1234567890", "충청북도 충주시 무학1길 30 (봉방동)", "127.93", "36.97", "미용", "96112", "K011", "Y", "Y"),
				TestXlsx.november("김창숙미용실", "1234567890", "충청북도 충주시 무학1길 30 (봉방동) 김창숙미용실", "127.93", "36.97", "네일", "96112", "K011", "N", "Y"),
				TestXlsx.november("새여울잔치국수", "2234567890", "대전광역시 대덕구 신탄진북로 35", "127.43", "36.45", "국수", "56111", null, "Y", "N"),
				TestXlsx.november("좌표없는가게", "3234567890", "서울특별시 중구 을지로 1", "0", "0", null, "00000", "K010", "Y", "Y")));
		List<Object> september = Arrays.asList("김창숙미용실", "1234567890", null, "충청북도 충주시 무학1길 30 (봉방동)", 36.97, 127.93,
				4413310800L, "전통시장", "테스트시장", "미용", "Y", "Y");
		Path sep = TestXlsx.write(DIR.resolve("sep.xlsx"), TestXlsx.SEPTEMBER_HEADER, List.of(september));
		String aiFp = LegacyStoreIds.aiFingerprint(new SourceRow(2, "김창숙미용실", "1234567890", null,
				"충청북도 충주시 무학1길 30 (봉방동)", 36.97, 127.93, "4413310800", "전통시장", "테스트시장", "미용", null, null, true, true));
		write(DIR.resolve("registry.csv"), "entity_fingerprint,store_id,created_dataset_version\n" + aiFp + "," + SEPTEMBER_ID + ",stores_v003\n");

		registry.add("app.load.source", nov::toString);
		registry.add("app.load.legacy-source", sep::toString);
		registry.add("app.load.legacy-registry", () -> DIR.resolve("registry.csv").toString());
		registry.add("app.load.dir", () -> DIR.resolve("load").toString());
		registry.add("app.load.verify-with-server", () -> "false");
	}

	@Autowired
	InitialLoadService service;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void prepareThenImportActivatesVersionOne() throws IOException {
		Map<String, Object> prepared = service.prepare();

		assertThat(prepared).containsEntry("stores", 3).containsEntry("mergedStores", 1).containsEntry("keptIds", 1)
				.containsEntry("coordErrors", 1);
		assertThat(jdbc.queryForObject("SELECT status FROM data_version", String.class)).isEqualTo("building");
		assertThat(jdbc.queryForObject("SELECT items FROM store WHERE store_id = ?", String.class, SEPTEMBER_ID)).isEqualTo("미용, 네일");
		assertThatThrownBy(service::prepare).hasMessageContaining("빈 DB");

		Path dir = DIR.resolve("load");
		List<EmbedFiles.Entry> inputs = EmbedFiles.readInput(dir.resolve(EmbedFiles.INPUT));
		assertThat(inputs).hasSize(3).extracting(EmbedFiles.Entry::storeId).contains(SEPTEMBER_ID);
		float[][] vectors = new float[3][1024];
		for (int i = 0; i < 3; i++) {
			vectors[i][i] = 1;                                  // 서로 다른 단위 벡터
		}
		writeNpy(dir.resolve(EmbedFiles.VECTORS), vectors);
		write(dir.resolve(EmbedFiles.MANIFEST), """
				{"model": "bge_m3_ft_mj_20261014_0743", "count": 3, "dimension": 1024, "dtype": "float32",
				 "normalized": true, "input_sha256": "%s", "device": "test"}""".formatted(EmbedFiles.sha256(dir.resolve(EmbedFiles.INPUT))));

		Map<String, Object> imported = service.importVectors();

		assertThat(imported).containsEntry("rows", 3L).containsEntry("searchable", 2);
		assertThat(jdbc.queryForObject("SELECT status FROM data_version", String.class)).isEqualTo("active");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM store_snapshot WHERE version_id = 1", Integer.class)).isEqualTo(3);
		int index = inputs.stream().map(EmbedFiles.Entry::storeId).toList().indexOf(SEPTEMBER_ID);
		assertThat(jdbc.queryForObject("SELECT (embedding <#> ?::vector) * -1 FROM store_snapshot WHERE store_id = ?", Double.class,
				Arrays.toString(vectors[index]), SEPTEMBER_ID)).isEqualTo(1.0);
		assertThat(jdbc.queryForMap("SELECT primary_category, category_source, address_short, payment_codes::text AS pay FROM store_snapshot WHERE store_id = ?",
				SEPTEMBER_ID)).containsEntry("primary_category", "K011").containsEntry("category_source", "official")
				.containsEntry("address_short", "충주시 무학1길").containsEntry("pay", "{card,qr}");
		assertThat(jdbc.queryForObject("SELECT primary_category FROM store_snapshot WHERE doc_text LIKE '%새여울%'", String.class))
				.as("업종이 비면 산업분류로").isEqualTo("K006");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM store_snapshot WHERE searchable AND ST_DWithin(geom, ST_MakePoint(127.93, 36.97)::geography, 100)",
				Integer.class)).isEqualTo(1);
	}

	static void writeNpy(Path file, float[][] rows) throws IOException {
		String dict = "{'descr': '<f4', 'fortran_order': False, 'shape': (" + rows.length + ", " + rows[0].length + "), }";
		int unpadded = 10 + dict.length() + 1;
		String header = dict + " ".repeat((64 - unpadded % 64) % 64) + "\n";
		ByteBuffer data = ByteBuffer.allocate(rows.length * rows[0].length * 4).order(ByteOrder.LITTLE_ENDIAN);
		for (float[] r : rows) {
			for (float x : r) {
				data.putFloat(x);
			}
		}
		try (OutputStream out = Files.newOutputStream(file)) {
			out.write(new byte[] {(byte) 0x93, 'N', 'U', 'M', 'P', 'Y', 1, 0});
			out.write(header.length() & 0xff);
			out.write(header.length() >> 8);
			out.write(header.getBytes(StandardCharsets.ISO_8859_1));
			out.write(data.array());
		}
	}

	static void write(Path file, String text) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, text);
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

}
