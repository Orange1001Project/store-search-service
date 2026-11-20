package com.storesearch.refresh.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.storesearch.common.text.Csv;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Colab과 주고받는 파일(docs/dev/DATA_LOAD.md).
 *
 * <ul>
 *   <li>{@value #INPUT}: store_id, doc_text (UTF-8 CSV, store_id 오름차순) — 적재 준비가 만든다
 *   <li>{@value #VECTORS}: float32 [가게 수, 1024], 입력 파일과 같은 순서 — Colab이 만든다
 *   <li>{@value #MANIFEST}: 모델·건수·입력 파일 sha256 — Colab이 만든다
 * </ul>
 */
final class EmbedFiles {

	static final String INPUT = "embed_input.csv";

	static final String VECTORS = "embeddings.npy";

	static final String MANIFEST = "embeddings.json";

	record Entry(UUID storeId, String docText) {
	}

	/** Colab 결과 설명 파일(파이썬 쪽 이름 그대로 snake_case) */
	record Manifest(String model, int count, int dimension, String dtype, Boolean normalized,
			@JsonProperty("input_sha256") String inputSha256) {
	}

	private EmbedFiles() {
	}

	static void writeInput(Path file, List<Entry> entries) {
		try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
			w.write(Csv.line("store_id", "doc_text"));
			for (Entry e : entries) {
				w.write(Csv.line(e.storeId().toString(), e.docText()));
			}
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static List<Entry> readInput(Path file) {
		List<Entry> entries = new ArrayList<>();
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			boolean[] header = {true};
			Csv.read(reader, row -> {
				if (header[0]) {
					header[0] = false;
					if (!row.equals(List.of("store_id", "doc_text"))) {
						throw new IllegalArgumentException(file + ": 머리글이 store_id,doc_text가 아닙니다");
					}
					return;
				}
				if (row.size() == 1 && row.get(0).isEmpty()) {
					return;
				}
				entries.add(new Entry(UUID.fromString(row.get(0)), row.get(1)));
			});
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return entries;
	}

	static String sha256(Path file) {
		try (InputStream in = new DigestInputStream(Files.newInputStream(file), MessageDigest.getInstance("SHA-256"))) {
			DigestInputStream din = (DigestInputStream) in;
			din.transferTo(java.io.OutputStream.nullOutputStream());
			return HexFormat.of().formatHex(din.getMessageDigest().digest());
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
