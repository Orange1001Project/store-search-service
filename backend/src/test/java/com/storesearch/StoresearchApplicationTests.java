package com.storesearch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// 로컬 DB와 같은 이미지(infra/db/Dockerfile)를 빈 컨테이너로 띄워 Flyway V1부터 적용해 본다.
// 이미지가 없으면 먼저: infra 폴더에서 docker compose build db
@SpringBootTest
@Testcontainers
class StoresearchApplicationTests {

	@Container
	@ServiceConnection
	static PostgreSQLContainer db = new PostgreSQLContainer(
			DockerImageName.parse("storesearch-db:16-postgis3-pgvector0.8").asCompatibleSubstituteFor("postgres"));

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void migratesEmptyDatabase() {
		assertThat(jdbc.queryForObject("SELECT count(*) FROM category", Integer.class)).isEqualTo(17);
	}

}
