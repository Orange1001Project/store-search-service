package com.storesearch.refresh.repository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** data_version 표. 버전 이름은 한국 날짜, 같은 날 두 번째부터 .2, .3(DB_TABLES.md 3절). */
@Repository
public class DataVersionRepository {

	public record Version(short id, String name, String status, String embeddingModel) {
	}

	public record Counts(int total, int newStores, int changed, int pending, int closed, int coordError) {
	}

	private final JdbcTemplate jdbc;

	public DataVersionRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public long count() {
		return jdbc.queryForObject("SELECT count(*) FROM data_version", Long.class);
	}

	public Version createBuilding(LocalDate kstDate, String embeddingModel) {
		String base = kstDate.toString();
		List<String> taken = jdbc.queryForList(
				"SELECT version_name FROM data_version WHERE version_name = ? OR version_name LIKE ?", String.class,
				base, base + ".%");
		String name = base;
		for (int n = 2; taken.contains(name); n++) {
			name = base + "." + n;
		}
		Short id = jdbc.queryForObject(
				"INSERT INTO data_version (version_name, status, embedding_model) VALUES (?, 'building', ?) RETURNING version_id",
				Short.class, name, embeddingModel);
		return new Version(id, name, "building", embeddingModel);
	}

	public List<Version> findByStatus(String status) {
		return jdbc.query("SELECT version_id, version_name, status, embedding_model FROM data_version WHERE status = ? ORDER BY version_id",
				(rs, i) -> new Version(rs.getShort(1), rs.getString(2), rs.getString(3), rs.getString(4)), status);
	}

	/** 이전 활성 버전을 내리고 이 버전을 올린다. 같은 트랜잭션에서 스냅샷 파티션을 붙여야 한다. */
	public void activate(short versionId, Counts counts) {
		jdbc.update("UPDATE data_version SET status = 'retired' WHERE status = 'active'");
		int updated = jdbc.update("""
				UPDATE data_version SET status = 'active', activated_at = now(),
				       n_total = ?, n_new = ?, n_changed = ?, n_pending = ?, n_closed = ?, n_coord_error = ?
				WHERE version_id = ? AND status = 'building'""",
				counts.total(), counts.newStores(), counts.changed(), counts.pending(), counts.closed(), counts.coordError(),
				versionId);
		if (updated != 1) {
			throw new IllegalStateException("만드는 중(building)인 버전 " + versionId + "이 없습니다");
		}
	}

	public static LocalDate kstToday(Clock clock) {
		return LocalDate.now(clock.withZone(java.time.ZoneId.of("Asia/Seoul")));
	}

	public Optional<Version> findOnlyBuilding() {
		List<Version> building = findByStatus("building");
		if (building.size() > 1) {
			throw new IllegalStateException("만드는 중인 버전이 여럿입니다: " + building);
		}
		return building.stream().findFirst();
	}

}
