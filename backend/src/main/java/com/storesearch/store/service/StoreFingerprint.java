package com.storesearch.store.service;

import com.storesearch.common.text.SourceText;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 가게 지문: 원천 파일의 행이 지난 버전의 어느 가게인지 판단한다(store.fingerprint, DB_TABLES.md 3절).
 *
 * <p>지문 = sha256(사업자번호 | 가맹점명 | 도로명 키). 도로명 키는 {@link RoadAddress#key()}.
 * 상세 주소(층·호·상호)는 매번 다르게 적혀 오므로 보지 않는다. 9월 → 11월 파일에서 전체 주소를 보면
 * 80.7%만 이어졌고, 도로명 키로 보면 89.8%가 이어졌다(2026-11-19 측정).
 */
public final class StoreFingerprint {

	private StoreFingerprint() {
	}

	public static String of(String businessNo, String storeName, String address) {
		return sha256(Objects.toString(SourceText.digits(businessNo), "") + "|"
				+ Objects.toString(SourceText.clean(storeName), "") + "|" + roadKey(address));
	}

	static String roadKey(String address) {
		return RoadAddress.parse(address).key();
	}

	static String sha256(String key) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
