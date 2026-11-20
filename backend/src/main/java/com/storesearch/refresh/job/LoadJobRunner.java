package com.storesearch.refresh.job;

import com.storesearch.refresh.service.InitialLoadService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 명령줄 작업. 웹 서버 없이 뜨고 작업이 끝나면 종료한다(StoresearchApplication.main).
 * <pre>
 * --app.job=load-prepare   원천 엑셀 → store + Colab 입력 파일
 * --app.job=load-import    Colab 벡터 → 스냅샷 파티션 → 버전 활성
 * </pre>
 * 사용법: docs/dev/DATA_LOAD.md
 */
@Component
@ConditionalOnProperty(name = "app.job")
public class LoadJobRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(LoadJobRunner.class);

	private final InitialLoadService initialLoad;

	private final String job;

	public LoadJobRunner(InitialLoadService initialLoad, org.springframework.core.env.Environment env) {
		this.initialLoad = initialLoad;
		this.job = env.getRequiredProperty("app.job");
	}

	@Override
	public void run(ApplicationArguments args) {
		Map<String, Object> report = switch (job) {
			case "load-prepare" -> initialLoad.prepare();
			case "load-import" -> initialLoad.importVectors();
			default -> throw new IllegalArgumentException("모르는 작업: " + job + " (load-prepare, load-import)");
		};
		log.info("{} 끝: {}", job, report);
	}

}
