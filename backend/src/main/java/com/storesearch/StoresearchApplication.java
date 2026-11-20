package com.storesearch;

import java.util.Arrays;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StoresearchApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(StoresearchApplication.class);
		// 명령줄 작업(--app.job=…, refresh/job)은 웹 서버 없이 실행하고 끝나면 종료한다
		boolean job = Arrays.stream(args).anyMatch(a -> a.startsWith("--app.job="));
		if (job) {
			app.setWebApplicationType(WebApplicationType.NONE);
		}
		ConfigurableApplicationContext context = app.run(args);
		if (job) {
			System.exit(SpringApplication.exit(context));
		}
	}

}
