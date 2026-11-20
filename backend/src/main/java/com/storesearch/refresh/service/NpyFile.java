package com.storesearch.refresh.service;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Colab이 만든 벡터 파일(numpy .npy, float32 리틀엔디안, 2차원 [가게 수, 1024], C 순서)을 읽는다.
 * numpy 형식 1.0·2.0·3.0 머리글을 받는다.
 */
final class NpyFile {

	private static final Pattern SHAPE = Pattern.compile("'shape':\\s*\\((\\d+),\\s*(\\d+)\\s*,?\\s*\\)");

	private final Path path;

	private final long dataOffset;

	private final int rows;

	private final int dimension;

	private NpyFile(Path path, long dataOffset, int rows, int dimension) {
		this.path = path;
		this.dataOffset = dataOffset;
		this.rows = rows;
		this.dimension = dimension;
	}

	static NpyFile open(Path path) {
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
			byte[] magic = in.readNBytes(6);
			if (magic.length != 6 || (magic[0] & 0xff) != 0x93 || !new String(magic, 1, 5, StandardCharsets.US_ASCII).equals("NUMPY")) {
				throw new IllegalArgumentException(path + ": .npy 파일이 아닙니다");
			}
			int major = in.readUnsignedByte();
			in.readUnsignedByte();
			long headerLength;
			int prefix;
			if (major == 1) {
				headerLength = Short.toUnsignedInt(Short.reverseBytes(in.readShort()));
				prefix = 10;
			}
			else {
				headerLength = Integer.toUnsignedLong(Integer.reverseBytes(in.readInt()));
				prefix = 12;
			}
			String header = new String(in.readNBytes((int) headerLength), major >= 3 ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1);
			if (!header.contains("'descr': '<f4'")) {
				throw new IllegalArgumentException(path + ": float32 리틀엔디안('<f4')이어야 합니다 — " + header.strip());
			}
			if (!header.contains("'fortran_order': False")) {
				throw new IllegalArgumentException(path + ": C 순서여야 합니다 — " + header.strip());
			}
			Matcher m = SHAPE.matcher(header);
			if (!m.find()) {
				throw new IllegalArgumentException(path + ": 2차원 배열이어야 합니다 — " + header.strip());
			}
			int rows = Integer.parseInt(m.group(1));
			int dim = Integer.parseInt(m.group(2));
			long offset = prefix + headerLength;
			long expected = offset + (long) rows * dim * Float.BYTES;
			if (Files.size(path) != expected) {
				throw new IllegalArgumentException(path + ": 파일 크기가 머리글과 다릅니다(" + Files.size(path) + " ≠ " + expected + ")");
			}
			return new NpyFile(path, offset, rows, dim);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	int rows() {
		return rows;
	}

	int dimension() {
		return dimension;
	}

	/** 한 행만 읽는다(표본 확인용) */
	float[] row(int index) {
		try (FileChannel ch = FileChannel.open(path, StandardOpenOption.READ)) {
			ByteBuffer buf = ByteBuffer.allocate(dimension * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
			long pos = dataOffset + (long) index * dimension * Float.BYTES;
			while (buf.hasRemaining()) {
				if (ch.read(buf, pos + buf.position()) < 0) {
					throw new EOFException();
				}
			}
			buf.flip();
			float[] v = new float[dimension];
			buf.asFloatBuffer().get(v);
			return v;
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** 처음부터 차례로 읽는 읽개 */
	Sequential sequential() {
		try {
			InputStream in = new BufferedInputStream(Files.newInputStream(path), 1 << 20);
			in.skipNBytes(dataOffset);
			return new Sequential(in, dimension);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static final class Sequential implements AutoCloseable {

		private final InputStream in;

		private final byte[] bytes;

		private final int dimension;

		Sequential(InputStream in, int dimension) {
			this.in = in;
			this.dimension = dimension;
			this.bytes = new byte[dimension * Float.BYTES];
		}

		float[] next() {
			try {
				int n = in.readNBytes(bytes, 0, bytes.length);
				if (n != bytes.length) {
					throw new EOFException("벡터 파일이 예상보다 짧습니다");
				}
				float[] v = new float[dimension];
				ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(v);
				return v;
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		@Override
		public void close() {
			try {
				in.close();
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

	}

}
