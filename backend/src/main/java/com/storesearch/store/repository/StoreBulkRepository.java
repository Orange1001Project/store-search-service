package com.storesearch.store.repository;

import com.storesearch.store.service.StoreSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** store 표를 한꺼번에 넣고 읽는다(적재·갱신용, JDBC 배치). 화면용 한 건 조회는 JPA 저장소로 따로 만든다. */
@Repository
public class StoreBulkRepository {

	public record NewStore(UUID storeId, StoreSource source) {
	}

	public record StoredStore(UUID storeId, StoreSource source, String lifecycleStatus, boolean hidden) {
	}

	private static final int BATCH = 1000;

	private final JdbcTemplate jdbc;

	public StoreBulkRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public long count() {
		return jdbc.queryForObject("SELECT count(*) FROM store", Long.class);
	}

	public void insertAll(short versionId, List<NewStore> stores) {
		String sql = """
				INSERT INTO store (store_id, fingerprint, store_name, business_no, merchant_no, address, latitude, longitude,
				                   legal_dong_code, market_type, market_name, items, ksic_code, onnuri_category,
				                   card_payment, qr_payment, source_conflict, coord_error, last_seen_version_id)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";
		jdbc.batchUpdate(sql, stores, BATCH, (PreparedStatement ps, NewStore n) -> {
			StoreSource s = n.source();
			ps.setObject(1, n.storeId());
			ps.setString(2, s.fingerprint());
			ps.setString(3, s.storeName());
			ps.setString(4, s.businessNo());
			ps.setString(5, s.merchantNo());
			ps.setString(6, s.address());
			ps.setBigDecimal(7, coordinate(s.latitude()));
			ps.setBigDecimal(8, coordinate(s.longitude()));
			ps.setString(9, s.legalDongCode());
			ps.setString(10, s.marketType());
			ps.setString(11, s.marketName());
			ps.setString(12, s.items());
			ps.setString(13, s.ksicCode());
			ps.setString(14, s.onnuriCategory());
			setBoolean(ps, 15, s.cardPayment());
			setBoolean(ps, 16, s.qrPayment());
			ps.setBoolean(17, s.sourceConflict());
			ps.setBoolean(18, s.coordError());
			ps.setShort(19, versionId);
		});
	}

	/** 그 버전에서 원천에 있던 가게 전체. 트랜잭션 안에서 부르면 커서로 나눠 읽는다. */
	public void forEachSeenIn(short versionId, Consumer<StoredStore> consumer) {
		jdbc.query(con -> {
			PreparedStatement ps = con.prepareStatement("""
					SELECT store_id, fingerprint, store_name, business_no, merchant_no, address, latitude, longitude,
					       legal_dong_code, market_type, market_name, items, ksic_code, onnuri_category,
					       card_payment, qr_payment, source_conflict, coord_error, lifecycle_status, is_hidden
					FROM store WHERE last_seen_version_id = ? ORDER BY store_id""");
			ps.setShort(1, versionId);
			ps.setFetchSize(5000);
			return ps;
		}, (ResultSet rs) -> {
			consumer.accept(new StoredStore(rs.getObject(1, UUID.class), new StoreSource(rs.getString(2), rs.getString(3),
					rs.getString(4), rs.getString(5), rs.getString(6), toDouble(rs.getBigDecimal(7)), toDouble(rs.getBigDecimal(8)),
					rs.getString(9), rs.getString(10), rs.getString(11), rs.getString(12), rs.getString(13), rs.getString(14),
					(Boolean) rs.getObject(15), (Boolean) rs.getObject(16), rs.getBoolean(17), rs.getBoolean(18)),
					rs.getString(19), rs.getBoolean(20)));
		});
	}

	/** numeric(9,6) 범위(±999.999999)를 넘는 원천 좌표는 넣을 수 없어 비운다(이미 좌표 오류로 표시됨). */
	static BigDecimal coordinate(Double value) {
		if (value == null || Math.abs(value) >= 1000) {
			return null;
		}
		return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
	}

	private static Double toDouble(BigDecimal value) {
		return value == null ? null : value.doubleValue();
	}

	private static void setBoolean(PreparedStatement ps, int index, Boolean value) throws SQLException {
		if (value == null) {
			ps.setNull(index, Types.BOOLEAN);
		}
		else {
			ps.setBoolean(index, value);
		}
	}

}
