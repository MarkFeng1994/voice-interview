package com.interview.module.media.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.interview.module.media.repository.MediaFileRecordRepository;

@Service
@Profile("dev")
public class MediaFileLifecycleService {

	private final MediaFileRecordRepository repository;
	private final Path storageRoot;
	private final long userAudioExpireHours;
	private final long generatedAudioExpireHours;

	public MediaFileLifecycleService(
			MediaFileRecordRepository repository,
			@Value("${app.media.storage-path:./storage/media}") String storagePath,
			@Value("${app.media.user-audio-expire-hours:168}") long userAudioExpireHours,
			@Value("${app.media.generated-audio-expire-hours:72}") long generatedAudioExpireHours
	) {
		this.repository = repository;
		this.storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
		this.userAudioExpireHours = userAudioExpireHours;
		this.generatedAudioExpireHours = generatedAudioExpireHours;
	}

	public MediaFileRecord recordUploadedFile(StoredMediaFile file, String userId) {
		return repository.save(
				userId,
				"USER_AUDIO",
				"LOCAL",
				file.storedFileName(),
				file.contentType(),
				0L,
				file.size(),
				LocalDateTime.now().plusHours(userAudioExpireHours)
		);
	}

	public MediaFileRecord recordGeneratedAudio(StoredMediaFile file, String userId, long durationMs) {
		return repository.save(
				userId,
				"TTS_AUDIO",
				"LOCAL",
				file.storedFileName(),
				file.contentType(),
				durationMs,
				file.size(),
				LocalDateTime.now().plusHours(generatedAudioExpireHours)
		);
	}

	@Scheduled(fixedDelayString = "${app.media.cleanup-fixed-delay-ms:1800000}")
	public void scheduledCleanupExpiredFiles() {
		cleanupExpiredFiles(200);
	}

	public int cleanupExpiredFiles(int limit) {
		List<MediaFileRecord> expiredRecords = repository.findExpired(LocalDateTime.now(), limit);
		for (MediaFileRecord record : expiredRecords) {
			deleteFileQuietly(storageRoot.resolve(record.fileKey()));
			repository.deleteById(record.id());
		}
		return expiredRecords.size();
	}

	private void deleteFileQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException ex) {
			// Best-effort cleanup
		}
	}
}
