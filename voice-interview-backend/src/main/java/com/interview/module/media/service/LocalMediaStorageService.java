package com.interview.module.media.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import com.interview.common.exception.AppException;
import com.interview.module.user.security.AuthContext;
import com.interview.module.user.service.UserProfile;

@Service
public class LocalMediaStorageService {

	private final Path storageRoot;
	private final MediaFileLifecycleService mediaFileLifecycleService;

	public LocalMediaStorageService(
			@Value("${app.media.storage-path:./storage/media}") String storagePath,
			ObjectProvider<MediaFileLifecycleService> mediaFileLifecycleService
	) {
		try {
			this.storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
			Files.createDirectories(this.storageRoot);
			this.mediaFileLifecycleService = mediaFileLifecycleService.getIfAvailable();
		} catch (IOException ex) {
			throw new UncheckedIOException("Failed to initialize local media storage", ex);
		}
	}

	public StoredMediaFile store(MultipartFile file) {
		if (file.isEmpty()) {
			throw new IllegalArgumentException("Uploaded file must not be empty");
		}

		String originalFileName = Optional.ofNullable(file.getOriginalFilename())
				.map(Path::of)
				.map(Path::getFileName)
				.map(Path::toString)
				.orElse("upload.bin");
		String fileId = UUID.randomUUID().toString().replace("-", "");
		String extension = extractExtension(originalFileName);
		String storedFileName = extension.isEmpty() ? fileId : fileId + extension;
		Path targetPath = storageRoot.resolve(storedFileName);

		try {
			Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
			String contentType = Optional.ofNullable(file.getContentType())
					.orElseGet(() -> probeContentType(targetPath));
			long size = Files.size(targetPath);
			StoredMediaFile storedFile = new StoredMediaFile(fileId, originalFileName, storedFileName, contentType, size, targetPath);
			recordUploadedFile(storedFile);
			return storedFile;
		} catch (IOException ex) {
			throw new AppException("MEDIA_STORE_FAILED", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "媒体文件保存失败", ex);
		}
	}

	public StoredMediaFile storeGenerated(byte[] content, String originalFileName, String contentType) {
		return storeGenerated(content, originalFileName, contentType, 0L);
	}

	public StoredMediaFile storeGenerated(byte[] content, String originalFileName, String contentType, long durationMs) {
		if (content.length == 0) {
			throw new IllegalArgumentException("Generated file content must not be empty");
		}

		String sanitizedFileName = Optional.ofNullable(originalFileName)
				.filter(name -> !name.isBlank())
				.map(Path::of)
				.map(Path::getFileName)
				.map(Path::toString)
				.orElse("generated.bin");
		String fileId = UUID.randomUUID().toString().replace("-", "");
		String extension = extractExtension(sanitizedFileName);
		String storedFileName = extension.isEmpty() ? fileId : fileId + extension;
		Path targetPath = storageRoot.resolve(storedFileName);

		try {
			Files.write(targetPath, content);
			long size = Files.size(targetPath);
			StoredMediaFile storedFile = new StoredMediaFile(fileId, sanitizedFileName, storedFileName, contentType, size, targetPath);
			recordGeneratedFile(storedFile, durationMs);
			return storedFile;
		} catch (IOException ex) {
			throw new AppException("MEDIA_STORE_FAILED", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "生成音频保存失败", ex);
		}
	}

	public StoredMediaFile load(String fileId) {
		Path filePath = resolvePath(fileId)
				.orElseThrow(() -> AppException.notFound("MEDIA_NOT_FOUND", "媒体文件不存在"));
		try {
			String storedFileName = filePath.getFileName().toString();
			String contentType = probeContentType(filePath);
			long size = Files.size(filePath);
			return new StoredMediaFile(fileId, storedFileName, storedFileName, contentType, size, filePath);
		} catch (IOException ex) {
			throw new AppException("MEDIA_READ_FAILED", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "读取媒体文件失败", ex);
		}
	}

	private void recordUploadedFile(StoredMediaFile storedFile) {
		if (mediaFileLifecycleService != null) {
			mediaFileLifecycleService.recordUploadedFile(storedFile, resolveCurrentUserId());
		}
	}

	private void recordGeneratedFile(StoredMediaFile storedFile, long durationMs) {
		if (mediaFileLifecycleService != null) {
			mediaFileLifecycleService.recordGeneratedAudio(storedFile, resolveCurrentUserId(), durationMs);
		}
	}

	private String resolveCurrentUserId() {
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		if (attributes == null) {
			return null;
		}
		Object currentUser = attributes.getAttribute(AuthContext.CURRENT_USER_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
		if (currentUser instanceof UserProfile profile) {
			return profile.id();
		}
		return null;
	}

	private Optional<Path> resolvePath(String fileId) {
		try (Stream<Path> fileStream = Files.list(storageRoot)) {
			return fileStream
					.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().startsWith(fileId))
					.sorted(Comparator.comparing(path -> path.getFileName().toString()))
					.findFirst();
		} catch (IOException ex) {
			throw new UncheckedIOException("Failed to resolve media file path", ex);
		}
	}

	private String probeContentType(Path path) {
		try {
			return Optional.ofNullable(Files.probeContentType(path))
					.orElse("application/octet-stream");
		} catch (IOException ex) {
			return "application/octet-stream";
		}
	}

	private String extractExtension(String fileName) {
		int dotIndex = fileName.lastIndexOf('.');
		if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
			return "";
		}
		return fileName.substring(dotIndex);
	}
}
