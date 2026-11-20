package com.storesearch.store.repository;

import com.storesearch.store.service.SnapshotRow;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyIn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 버전별 스냅샷 파티션(store_snapshot_v{N}). 빈 표로 만들어 COPY로 채우고 인덱스까지 만든 뒤 붙인다
 * (V1__init.sql 끝 예시, DB_TABLES.md 3절). 붙이기 전에는 검색에 보이지 않는다.
 */
@Repository
public class SnapshotPartitionRepository {

	/** 벡터 한 행과 짝지은 스냅샷 행 */
	public record Entry(SnapshotRow row, float[] embedding) {
	}

	private static final String COLUMNS = "version_id, store_id, store_name, items, address, address_short, legal_dong_code, "
			+ "market_type, market_name, geom, category_codes, primary_category, category_source, subcategory, payment_codes, "
			+ "searchable, doc_text, embedding";

	private final JdbcTemplate jdbc;

	private final DataSource dataSource;

	public SnapshotPartitionRepository(JdbcTemplate jdbc, DataSource dataSource) {
		this.jdbc = jdbc;
		this.dataSource = dataSource;
	}

	static String table(short versionId) {
		return "store_snapshot_v" + versionId;
	}

	public boolean isAttached(short versionId) {
		return Boolean.TRUE.equals(jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
				               WHERE i.inhparent = 'store_snapshot'::regclass AND c.relname = ?)""",
				Boolean.class, table(versionId)));
	}

	/** 빈 파티션 표를 새로 만든다. 붙지 않은 이전 시도 표가 있으면 지운다. */
	public void createEmpty(short versionId) {
		if (isAttached(versionId)) {
			throw new IllegalStateException(table(versionId) + "은 이미 검색에 붙어 있습니다");
		}
		jdbc.execute("DROP TABLE IF EXISTS " + table(versionId));
		jdbc.execute("CREATE TABLE " + table(versionId) + " (LIKE store_snapshot INCLUDING DEFAULTS INCLUDING CONSTRAINTS)");
	}

	/** COPY(text 형식)로 채운다. 넣은 행 수를 돌려준다. */
	public long copy(short versionId, Iterator<Entry> entries) {
		try (Connection con = dataSource.getConnection()) {
			CopyIn copy = con.unwrap(PGConnection.class).getCopyAPI()
					.copyIn("COPY " + table(versionId) + " (" + COLUMNS + ") FROM STDIN (FORMAT text)");
			try {
				StringBuilder sb = new StringBuilder(16 * 1024);
				while (entries.hasNext()) {
					appendLine(sb, versionId, entries.next());
					if (sb.length() > 4 * 1024 * 1024) {
						write(copy, sb);
					}
				}
				write(copy, sb);
				return copy.endCopy();
			}
			finally {
				if (copy.isActive()) {
					copy.cancelCopy();
				}
			}
		}
		catch (SQLException e) {
			throw new IllegalStateException("스냅샷 COPY 실패: " + e.getMessage(), e);
		}
	}

	/** PK·인덱스·버전 CHECK를 만들고 통계를 갱신한다(붙이기 전에). */
	public void finish(short versionId) {
		String t = table(versionId);
		jdbc.execute("ALTER TABLE " + t + " ADD PRIMARY KEY (version_id, store_id)");
		jdbc.execute("CREATE INDEX ix_" + t + "_geom ON " + t + " USING gist (geom) WHERE searchable");
		jdbc.execute("CREATE INDEX ix_" + t + "_code ON " + t + " (legal_dong_code varchar_pattern_ops) WHERE searchable");
		jdbc.execute("ALTER TABLE " + t + " ADD CONSTRAINT ck_" + t + "_version CHECK (version_id = " + versionId + ")");
		jdbc.execute("ANALYZE " + t);
	}

	/** 전환 전 검증(DB_TABLES.md 9절): store에 없는 가게 번호, 다른 버전 번호, 코드표에 없는 업종·결제 */
	public Map<String, Long> verify(short versionId) {
		String t = table(versionId);
		return Map.of(
				"missingStore", count("SELECT count(*) FROM " + t + " s WHERE NOT EXISTS (SELECT 1 FROM store WHERE store_id = s.store_id)"),
				"wrongVersion", count("SELECT count(*) FROM " + t + " WHERE version_id <> " + versionId),
				"unknownCategory", count("SELECT count(*) FROM " + t + " s WHERE NOT EXISTS (SELECT 1 FROM category c WHERE c.category_code = s.primary_category)"
						+ " OR NOT s.category_codes <@ (SELECT array_agg(category_code)::varchar(4)[] FROM category)"),
				"unknownPayment", count("SELECT count(*) FROM " + t + " s WHERE NOT s.payment_codes <@ (SELECT array_agg(payment_code)::varchar(10)[] FROM payment_method)"));
	}

	/** 전환 트랜잭션 안에서 부른다 */
	public void attach(short versionId) {
		jdbc.execute("ALTER TABLE store_snapshot ATTACH PARTITION " + table(versionId) + " FOR VALUES IN (" + versionId + ")");
	}

	public long rowCount(short versionId) {
		return count("SELECT count(*) FROM " + table(versionId));
	}

	private long count(String sql) {
		Long n = jdbc.queryForObject(sql, Long.class);
		return n == null ? 0 : n;
	}

	private static void write(CopyIn copy, StringBuilder sb) throws SQLException {
		if (sb.isEmpty()) {
			return;
		}
		byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
		copy.writeToCopy(bytes, 0, bytes.length);
		sb.setLength(0);
	}

	static void appendLine(StringBuilder sb, short versionId, Entry e) {
		SnapshotRow r = e.row();
		sb.append(versionId).append('\t');
		sb.append(r.storeId()).append('\t');
		text(sb, r.storeName()).append('\t');
		text(sb, r.items()).append('\t');
		text(sb, r.address()).append('\t');
		text(sb, r.addressShort()).append('\t');
		text(sb, r.legalDongCode()).append('\t');
		text(sb, r.marketType()).append('\t');
		text(sb, r.marketName()).append('\t');
		sb.append("SRID=4326;POINT(").append(r.longitude()).append(' ').append(r.latitude()).append(")\t");
		array(sb, r.categoryCodes()).append('\t');
		text(sb, r.primaryCategory()).append('\t');
		text(sb, r.categorySource()).append('\t');
		text(sb, r.subcategory()).append('\t');
		array(sb, r.paymentCodes()).append('\t');
		sb.append(r.searchable() ? 't' : 'f').append('\t');
		text(sb, r.docText()).append('\t');
		sb.append('[');
		float[] v = e.embedding();
		for (int i = 0; i < v.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(v[i]);
		}
		sb.append("]\n");
	}

	/** COPY text 형식 값: null은 \N, 역슬래시·탭·줄바꿈은 이스케이프 */
	private static StringBuilder text(StringBuilder sb, String value) {
		if (value == null) {
			return sb.append("\\N");
		}
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '\\' -> sb.append("\\\\");
				case '\t' -> sb.append("\\t");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				default -> sb.append(c);
			}
		}
		return sb;
	}

	/** 코드 목록(영문·숫자만)을 배열 리터럴로 */
	private static StringBuilder array(StringBuilder sb, List<String> codes) {
		return sb.append('{').append(String.join(",", codes)).append('}');
	}

}
