package com.storesearch.common.text;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** RFC 4180 CSV(따옴표·쉼표·줄바꿈 포함 값). 시드·AI 레포 파일과 Colab 입력 파일에 쓴다. */
public final class Csv {

	private Csv() {
	}

	/** 행마다 consumer를 부른다. 첫 행(머리글)도 넘긴다. UTF-8 BOM은 지운다. */
	public static void read(Reader source, Consumer<List<String>> consumer) {
		Reader reader = source.markSupported() ? source : new BufferedReader(source);
		try {
			List<String> row = new ArrayList<>();
			StringBuilder cell = new StringBuilder();
			boolean quoted = false;
			boolean first = true;
			boolean any = false;
			int c;
			while ((c = reader.read()) != -1) {
				if (first) {
					first = false;
					if (c == '﻿') {
						continue;
					}
				}
				any = true;
				if (quoted) {
					if (c == '"') {
						reader.mark(1);
						int next = reader.read();
						if (next == '"') {
							cell.append('"');
						}
						else {
							quoted = false;
							if (next == -1) {
								break;
							}
							reader.reset();
						}
					}
					else {
						cell.append((char) c);
					}
				}
				else if (c == '"') {
					quoted = true;
				}
				else if (c == ',') {
					row.add(cell.toString());
					cell.setLength(0);
				}
				else if (c == '\n') {
					row.add(cell.toString());
					cell.setLength(0);
					consumer.accept(row);
					row = new ArrayList<>();
					any = false;
				}
				else if (c != '\r') {
					cell.append((char) c);
				}
			}
			if (any || !row.isEmpty() || cell.length() > 0) {
				row.add(cell.toString());
				consumer.accept(row);
			}
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** 한 행을 CSV 줄로(끝에 \n). 쉼표·따옴표·줄바꿈이 있으면 따옴표로 감싼다. */
	public static String line(String... values) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			String v = values[i] == null ? "" : values[i];
			if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
				sb.append('"').append(v.replace("\"", "\"\"")).append('"');
			}
			else {
				sb.append(v);
			}
		}
		return sb.append('\n').toString();
	}

}
