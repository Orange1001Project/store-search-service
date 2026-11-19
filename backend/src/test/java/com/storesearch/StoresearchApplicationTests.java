package com.storesearch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

// 빈 DB에 Flyway V1부터 적용되는지 확인한다.
@SpringBootTest
@Import(TestcontainersConfig.class)
class StoresearchApplicationTests {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void migratesEmptyDatabase() {
		assertThat(jdbc.queryForObject("SELECT count(*) FROM category", Integer.class)).isEqualTo(17);
	}

}
