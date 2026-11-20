package com.storesearch.refresh.source;

/**
 * 원천 엑셀 한 행(정리한 값). 빈 값·{@code [NULL]}은 null.
 *
 * @param rowNumber 엑셀 행 번호(머리글 = 1)
 * @param businessNo 숫자만(앞자리 0 유지)
 * @param latitude 원천 Y좌표(이전 파일 LAT). 숫자가 아니면 null
 * @param longitude 원천 X좌표(이전 파일 LOT)
 * @param ksicCode 표준산업분류코드 5자리(이전 파일에는 없음)
 * @param onnuriCategory 온누리중분류 K001~K017(이전 파일에는 없음)
 * @param qrPayment 원천 QR결제(이전 파일 모바일결제여부)
 */
public record SourceRow(
		int rowNumber,
		String storeName,
		String businessNo,
		String merchantNo,
		String address,
		Double latitude,
		Double longitude,
		String legalDongCode,
		String marketType,
		String marketName,
		String itemsRaw,
		String ksicCode,
		String onnuriCategory,
		Boolean cardPayment,
		Boolean qrPayment) {
}
