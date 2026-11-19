package com.storesearch;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트용 DB. 로컬 DB와 같은 이미지(infra/db/Dockerfile)를 빈 컨테이너로 띄우고 Flyway가 V1부터 적용한다.
 * 테스트 클래스에 {@code @Import(TestcontainersConfig.class)}를 붙여 쓴다. 같은 설정의 테스트끼리는 컨테이너 하나를 같이 쓴다.
 * 이미지가 없으면 먼저: infra 폴더에서 docker compose build db
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

	public static final DockerImageName DB_IMAGE = DockerImageName.parse("storesearch-db:16-postgis3-pgvector0.8")
		.asCompatibleSubstituteFor("postgres");

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgres() {
		return new PostgreSQLContainer(DB_IMAGE);
	}

}
