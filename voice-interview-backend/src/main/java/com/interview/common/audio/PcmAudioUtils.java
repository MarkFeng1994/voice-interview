package com.interview.common.audio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import com.interview.common.exception.AppException;

public final class PcmAudioUtils {

	private PcmAudioUtils() {
	}

	public static byte[] toPcm16Mono(Path path, String contentType, String originalFileName, int sampleRate) {
		if (isRawPcm(contentType, originalFileName)) {
			try {
				return Files.readAllBytes(path);
			} catch (IOException ex) {
				throw AppException.badRequest("AUDIO_READ_FAILED", "读取 PCM 音频失败");
			}
		}

		AudioFormat targetFormat = new AudioFormat(
				AudioFormat.Encoding.PCM_SIGNED,
				sampleRate,
				16,
				1,
				2,
				sampleRate,
				false
		);

		try (AudioInputStream sourceStream = AudioSystem.getAudioInputStream(path.toFile());
			 AudioInputStream pcmStream = AudioSystem.getAudioInputStream(targetFormat, sourceStream);
			 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			pcmStream.transferTo(out);
			return out.toByteArray();
		} catch (Exception ex) {
			throw AppException.badRequest("AUDIO_FORMAT_UNSUPPORTED", "当前 ASR 仅支持可转换为 PCM/WAV 的音频");
		}
	}

	public static List<byte[]> chunk(byte[] content, int chunkSize) {
		List<byte[]> chunks = new ArrayList<>();
		if (content == null || content.length == 0) {
			return chunks;
		}
		for (int offset = 0; offset < content.length; offset += chunkSize) {
			int nextLength = Math.min(chunkSize, content.length - offset);
			byte[] chunk = new byte[nextLength];
			System.arraycopy(content, offset, chunk, 0, nextLength);
			chunks.add(chunk);
		}
		return chunks;
	}

	public static byte[] wrapPcm16MonoAsWav(byte[] pcmContent, int sampleRate) {
		int dataSize = pcmContent == null ? 0 : pcmContent.length;
		ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);

		buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII));
		buffer.putInt(36 + dataSize);
		buffer.put("WAVE".getBytes(StandardCharsets.US_ASCII));
		buffer.put("fmt ".getBytes(StandardCharsets.US_ASCII));
		buffer.putInt(16);
		buffer.putShort((short) 1);
		buffer.putShort((short) 1);
		buffer.putInt(sampleRate);
		buffer.putInt(sampleRate * 2);
		buffer.putShort((short) 2);
		buffer.putShort((short) 16);
		buffer.put("data".getBytes(StandardCharsets.US_ASCII));
		buffer.putInt(dataSize);
		if (pcmContent != null) {
			buffer.put(pcmContent);
		}
		return buffer.array();
	}

	public static long estimateDurationMs(int pcmBytesLength, int sampleRate) {
		if (pcmBytesLength <= 0 || sampleRate <= 0) {
			return 0L;
		}
		long sampleCount = pcmBytesLength / 2L;
		return Math.max(1L, Math.round(sampleCount * 1000.0d / sampleRate));
	}

	private static boolean isRawPcm(String contentType, String originalFileName) {
		String normalizedType = contentType == null ? "" : contentType.toLowerCase();
		String normalizedName = originalFileName == null ? "" : originalFileName.toLowerCase();
		return normalizedType.contains("audio/pcm")
				|| normalizedType.contains("audio/l16")
				|| normalizedName.endsWith(".pcm");
	}
}
