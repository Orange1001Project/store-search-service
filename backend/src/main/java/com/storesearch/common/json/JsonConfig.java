package com.storesearch.common.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.Annotated;
import tools.jackson.databind.introspect.JacksonAnnotationIntrospector;

/**
 * 빈 값 규칙(API_RULES.md 2절): 명세에 nullable인 필드는 null로 주고, 아닌 선택 필드는 뺀다.
 * 생성된 DTO(com.storesearch.api.model)는 선택 필드에만 {@code @Nullable}을 붙이므로,
 * 그 필드는 null이면 빼고 나머지(필수 필드, 필수 + nullable 필드)는 null이어도 넣는다.
 */
@Configuration(proxyBeanMethods = false)
public class JsonConfig {

	static final String MODEL_PACKAGE = "com.storesearch.api.model";

	@Bean
	JsonMapperBuilderCustomizer apiNullInclusion() {
		return builder -> builder.annotationIntrospector(new ApiModelIntrospector());
	}

	static class ApiModelIntrospector extends JacksonAnnotationIntrospector {

		@Override
		public JsonInclude.Value findPropertyInclusion(MapperConfig<?> config, Annotated a) {
			JsonInclude.Value base = super.findPropertyInclusion(config, a);
			if (a.getRawType() != null && isModelMember(a) && a.hasAnnotation(Nullable.class)) {
				return base.withValueInclusion(JsonInclude.Include.NON_NULL);
			}
			return base;
		}

		private static boolean isModelMember(Annotated a) {
			if (a instanceof tools.jackson.databind.introspect.AnnotatedMember member) {
				Class<?> owner = member.getDeclaringClass();
				return owner != null && MODEL_PACKAGE.equals(owner.getPackageName());
			}
			return false;
		}

	}

}
