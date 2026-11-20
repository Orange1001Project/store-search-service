package com.storesearch.refresh.service;

import com.storesearch.common.text.Csv;
import com.storesearch.refresh.source.SourceFileReader;
import com.storesearch.refresh.source.SourceRow;
import com.storesearch.store.service.StoreFingerprint;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 첫 적재 때 AI 레포가 발급한 store_id를 이어받는다(DB_TABLES.md 3절). 평가 정답(qrels)이 이 id로 연결되어 있다.
 *
 * <p>입력: 9월 원천 엑셀, AI 레지스트리(entity_fingerprint → store_id, 등록 순서), 평가 정답 파일.
 * 9월 행마다 AI 지문으로 id를 찾고, 서비스 지문으로 묶는다. 한 서비스 지문에 id가 여럿이면
 * 평가 정답에 나오는 id → 레지스트리에 먼저 등록된 id를 남기고, 나머지는 별칭(평가 때 바꿔 읽음)으로 돌려준다.
 */
final class LegacyStoreIds {

	record Alias(UUID oldId, UUID keptId) {
	}

	/** unmatchedRows: AI id를 못 찾은 이전 원천 행 번호(최대 100개, 확인용) */
	record Result(Map<String, UUID> idByFingerprint, List<Alias> aliases, int sourceRows, int matchedRows,
			List<Integer> unmatchedRows) {
	}

	private LegacyStoreIds() {
	}

	static Result build(Path septemberSource, Path registryCsv, Path qrelsCsv) {
		Map<String, Integer> order = new HashMap<>();
		Map<String, UUID> idByAiFingerprint = readRegistry(registryCsv, order);
		Set<UUID> judged = qrelsCsv == null ? Set.of() : readJudged(qrelsCsv);

		List<SourceRow> rows = SourceFileReader.read(septemberSource).rows();
		Map<String, Set<UUID>> candidates = new HashMap<>();
		int matched = 0;
		List<Integer> unmatched = new ArrayList<>();
		for (SourceRow r : rows) {
			UUID id = idByAiFingerprint.get(aiFingerprint(r));
			if (id == null) {
				if (unmatched.size() < 100) {
					unmatched.add(r.rowNumber());
				}
				continue;
			}
			matched++;
			candidates.computeIfAbsent(StoreFingerprint.of(r.businessNo(), r.storeName(), r.address()),
					k -> new LinkedHashSet<>()).add(id);
		}

		Comparator<UUID> preference = Comparator.comparing((UUID id) -> !judged.contains(id))
				.thenComparing(id -> order.getOrDefault(id.toString(), Integer.MAX_VALUE));
		Map<String, UUID> result = new HashMap<>();
		List<Alias> aliases = new ArrayList<>();
		for (Map.Entry<String, Set<UUID>> e : candidates.entrySet()) {
			List<UUID> ids = e.getValue().stream().sorted(preference).toList();
			UUID kept = ids.get(0);
			result.put(e.getKey(), kept);
			for (UUID other : ids.subList(1, ids.size())) {
				aliases.add(new Alias(other, kept));
			}
		}
		aliases.sort(Comparator.comparing(a -> a.oldId().toString()));
		return new Result(result, aliases, rows.size(), matched, unmatched);
	}

	/**
	 * AI 레포 ids.build_entity_fingerprint: sha256(사업자번호 | 상호.casefold() | 전체 주소.casefold()).
	 * 값은 원천 정리 규칙(SourceText)을 거친 것. 한글·영문·숫자에서는 casefold와 소문자 변환이 같다.
	 */
	static String aiFingerprint(SourceRow r) {
		String key = nz(r.businessNo()) + "|" + nz(r.storeName()).toLowerCase(Locale.ROOT) + "|"
				+ nz(r.address()).toLowerCase(Locale.ROOT);
		return sha256(key);
	}

	/**
	 * AI 레포는 엑셀을 pandas 기본값으로 읽어 "nan"·"NULL"·"NA" 같은 셀을 빈 값으로 보았다(9월 파일 상호 "nan" 2곳).
	 * 같은 지문을 얻으려고 그 값을 빈 값으로 본다.
	 */
	private static final Set<String> PANDAS_NA = Set.of("", "#N/A", "#N/A N/A", "#NA", "-1.#IND", "-1.#QNAN", "-NaN", "-nan",
			"1.#IND", "1.#QNAN", "<NA>", "N/A", "NA", "NULL", "NaN", "None", "n/a", "nan", "null");

	private static String nz(String s) {
		return s == null || PANDAS_NA.contains(s) ? "" : s;
	}

	private static String sha256(String key) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(hash);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** entity_fingerprint,store_id[,…] — 등록 순서를 order에 남긴다 */
	static Map<String, UUID> readRegistry(Path csv, Map<String, Integer> order) {
		Map<String, UUID> map = new HashMap<>();
		readCsv(csv, (header, row) -> {
			int fp = header.indexOf("entity_fingerprint");
			int id = header.indexOf("store_id");
			if (fp < 0 || id < 0) {
				throw new IllegalArgumentException(csv + ": entity_fingerprint, store_id 열이 필요합니다");
			}
			UUID storeId = UUID.fromString(row.get(id));
			map.put(row.get(fp), storeId);
			order.putIfAbsent(storeId.toString(), order.size());
		});
		return map;
	}

	/** query_id,doc_id,relevance — 판정된 doc_id 전체 */
	static Set<UUID> readJudged(Path csv) {
		Set<UUID> ids = new HashSet<>();
		readCsv(csv, (header, row) -> {
			int doc = header.indexOf("doc_id");
			if (doc < 0) {
				throw new IllegalArgumentException(csv + ": doc_id 열이 필요합니다");
			}
			ids.add(UUID.fromString(row.get(doc)));
		});
		return ids;
	}

	private interface RowHandler {

		void handle(List<String> header, List<String> row);

	}

	private static void readCsv(Path csv, RowHandler handler) {
		try (Reader reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
			List<List<String>> header = new ArrayList<>(1);
			Csv.read(reader, row -> {
				if (header.isEmpty()) {
					header.add(row);
				}
				else if (!(row.size() == 1 && row.get(0).isEmpty())) {
					handler.handle(header.get(0), row);
				}
			});
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
