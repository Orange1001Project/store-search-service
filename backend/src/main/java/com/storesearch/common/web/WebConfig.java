package com.storesearch.common.web;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

	public static final String API_PREFIX = "/api/v1";

	/** 생성된 인터페이스의 경로(/meta, /admin/...)에 명세 servers의 /api/v1을 붙인다. */
	@Override
	public void configurePathMatch(PathMatchConfigurer configurer) {
		configurer.addPathPrefix(API_PREFIX,
				HandlerTypePredicate.forBasePackage("com.storesearch").and(HandlerTypePredicate.forAnnotation(RestController.class)));
	}

	/**
	 * 경로·쿼리의 enum 값은 소문자·camelCase(예: lifecycleStatus=active, field=storeName)라 Spring 기본 변환(이름 그대로)과 다르다.
	 * 생성된 enum의 fromValue로 바꾼다(API_RULES.md 12절).
	 */
	@Override
	public void addFormatters(FormatterRegistry registry) {
		registry.addConverterFactory(new ApiEnumConverterFactory());
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	static class ApiEnumConverterFactory implements ConverterFactory<String, Enum> {

		@Override
		public <T extends Enum> Converter<String, T> getConverter(Class<T> targetType) {
			Method fromValue = findFromValue(targetType);
			if (fromValue == null) {
				return source -> (T) Enum.valueOf(targetType, source.trim());
			}
			return source -> {
				try {
					return (T) fromValue.invoke(null, source.trim());
				}
				catch (ReflectiveOperationException e) {
					throw new IllegalArgumentException(e.getCause() != null ? e.getCause() : e);
				}
			};
		}

		private static Method findFromValue(Class<?> type) {
			try {
				Method m = type.getMethod("fromValue", String.class);
				return Modifier.isStatic(m.getModifiers()) ? m : null;
			}
			catch (NoSuchMethodException e) {
				return null;
			}
		}

	}

}
