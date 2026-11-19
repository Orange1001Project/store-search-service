package com.storesearch.embedding;

/** 적재·갱신 중 임베딩 서버를 쓸 수 없음. 작업을 멈추고 실패로 남긴다. */
public class EmbeddingUnavailableException extends RuntimeException {

	public EmbeddingUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

}
