package com.interview.module.media.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.interview.common.result.ApiResponse;
import com.interview.module.media.service.LocalMediaStorageService;
import com.interview.module.media.service.StoredMediaFile;

@RestController
@RequestMapping("/api/media")
public class MediaController {

	private final LocalMediaStorageService mediaStorageService;

	public MediaController(LocalMediaStorageService mediaStorageService) {
		this.mediaStorageService = mediaStorageService;
	}

	@PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<MediaUploadPayload> upload(@RequestParam("file") MultipartFile file) {
		return ApiResponse.success(storeAndBuildPayload(file));
	}

	@PostMapping(value = "/upload/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ApiResponse<MediaUploadPayload> uploadResume(@RequestParam("file") MultipartFile file) {
		requirePdf(file);
		return ApiResponse.success(storeAndBuildPayload(file));
	}

	@GetMapping("/{fileId}")
	public ResponseEntity<Resource> getMedia(@PathVariable String fileId) throws IOException {
		StoredMediaFile storedFile = mediaStorageService.load(fileId);
		Resource resource = new UrlResource(storedFile.path().toUri());
		String encodedFileName = URLEncoder.encode(storedFile.storedFileName(), StandardCharsets.UTF_8)
				.replace("+", "%20");
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(storedFile.contentType()))
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encodedFileName)
				.body(resource);
	}

	public record MediaUploadPayload(
			String fileId,
			String fileName,
			String contentType,
			long size,
			String url
	) {
	}

	private MediaUploadPayload storeAndBuildPayload(MultipartFile file) {
		StoredMediaFile storedFile = mediaStorageService.store(file);
		String url = ServletUriComponentsBuilder.fromCurrentContextPath()
				.path("/api/media/")
				.path(storedFile.fileId())
				.toUriString();
		return new MediaUploadPayload(
				storedFile.fileId(),
				storedFile.originalFileName(),
				storedFile.contentType(),
				storedFile.size(),
				url
		);
	}

	private void requirePdf(MultipartFile file) {
		String contentType = file.getContentType();
		String originalName = file.getOriginalFilename();
		boolean isPdf = (contentType != null && contentType.contains("pdf"))
				|| (originalName != null && originalName.toLowerCase().endsWith(".pdf"));
		if (!isPdf) {
			throw new IllegalArgumentException("只支持 PDF 格式的简历文件");
		}
	}
}
