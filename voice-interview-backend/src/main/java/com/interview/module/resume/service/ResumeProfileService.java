package com.interview.module.resume.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.interview.module.resume.repository.ResumeProfileRepository;

@Service
@Profile("dev")
public class ResumeProfileService {

	private final Store store;

	public ResumeProfileService(ResumeProfileRepository repository) {
		this.store = new Store() {
			@Override
			public ResumeProfile savePendingProfile(String userId, String mediaFileId, String fileName, String mimeType, long sizeBytes) {
				return repository.savePendingProfile(userId, mediaFileId, fileName, mimeType, sizeBytes);
			}

			@Override
			public ResumeProfile findByResumeId(String userId, String resumeId) {
				return repository.findByResumeId(userId, resumeId);
			}

			@Override
			public ResumeProfile markParsing(String userId, String resumeId) {
				return repository.markParsing(userId, resumeId);
			}

			@Override
			public ResumeProfile markParsed(String userId, String resumeId, String resumeSummary, List<String> extractedKeywords,
					List<String> projectHighlights) {
				return repository.markParsed(userId, resumeId, resumeSummary, extractedKeywords, projectHighlights);
			}

			@Override
			public ResumeProfile markFailed(String userId, String resumeId, String parseError) {
				return repository.markFailed(userId, resumeId, parseError);
			}
		};
	}

	private ResumeProfileService(Store store) {
		this.store = store;
	}

	public static ResumeProfileService inMemory() {
		return new ResumeProfileService(new InMemoryStore());
	}

	public ResumeProfile createPendingProfile(String userId, String mediaFileId, String fileName, String mimeType, long sizeBytes) {
		validateRequired(userId, "userId");
		validateRequired(mediaFileId, "mediaFileId");
		validateRequired(fileName, "fileName");
		validateRequired(mimeType, "mimeType");
		if (sizeBytes < 0) {
			throw new IllegalArgumentException("sizeBytes must not be negative");
		}
		return store.savePendingProfile(userId, mediaFileId, fileName, mimeType, sizeBytes);
	}

	public ResumeProfile getDetail(String userId, String resumeId) {
		validateRequired(userId, "userId");
		validateRequired(resumeId, "resumeId");
		return store.findByResumeId(userId, resumeId);
	}

	public ResumeProfile markParsing(String userId, String resumeId) {
		validateRequired(userId, "userId");
		validateRequired(resumeId, "resumeId");
		return store.markParsing(userId, resumeId);
	}

	public ResumeProfile markParsed(
			String userId,
			String resumeId,
			String resumeSummary,
			List<String> extractedKeywords,
			List<String> projectHighlights
	) {
		validateRequired(userId, "userId");
		validateRequired(resumeId, "resumeId");
		return store.markParsed(userId, resumeId, resumeSummary, extractedKeywords, projectHighlights);
	}

	public ResumeProfile markFailed(String userId, String resumeId, String parseError) {
		validateRequired(userId, "userId");
		validateRequired(resumeId, "resumeId");
		validateRequired(parseError, "parseError");
		return store.markFailed(userId, resumeId, parseError);
	}

	private interface Store {
		ResumeProfile savePendingProfile(String userId, String mediaFileId, String fileName, String mimeType, long sizeBytes);

		ResumeProfile findByResumeId(String userId, String resumeId);

		ResumeProfile markParsing(String userId, String resumeId);

		ResumeProfile markParsed(String userId, String resumeId, String resumeSummary, List<String> extractedKeywords,
				List<String> projectHighlights);

		ResumeProfile markFailed(String userId, String resumeId, String parseError);
	}

	private static final class InMemoryStore implements Store {
		private final AtomicLong idGenerator = new AtomicLong(1L);
		private final Map<String, ResumeProfile> profiles = new ConcurrentHashMap<>();

		@Override
		public ResumeProfile savePendingProfile(String userId, String mediaFileId, String fileName, String mimeType, long sizeBytes) {
			ResumeProfile profile = new ResumeProfile(
					String.valueOf(idGenerator.getAndIncrement()),
					userId,
					mediaFileId,
					fileName,
					mimeType,
					sizeBytes,
					"UPLOADED",
					null,
					List.of(),
					List.of(),
					null
			);
			profiles.put(profile.resumeId(), profile);
			return profile;
		}

		@Override
		public ResumeProfile findByResumeId(String userId, String resumeId) {
			ResumeProfile profile = profiles.get(resumeId);
			if (profile == null || !profile.userId().equals(userId)) {
				throw new IllegalArgumentException("简历不存在");
			}
			return profile;
		}

		@Override
		public ResumeProfile markParsing(String userId, String resumeId) {
			return updateProfile(userId, resumeId, profile -> new ResumeProfile(
					profile.resumeId(),
					profile.userId(),
					profile.mediaFileId(),
					profile.originalFileName(),
					profile.contentType(),
					profile.sizeBytes(),
					"PARSING",
					profile.resumeSummary(),
					profile.extractedKeywords(),
					profile.projectHighlights(),
					null
			));
		}

		@Override
		public ResumeProfile markParsed(String userId, String resumeId, String resumeSummary, List<String> extractedKeywords,
				List<String> projectHighlights) {
			List<String> keywords = extractedKeywords == null ? List.of() : List.copyOf(extractedKeywords);
			List<String> highlights = projectHighlights == null ? List.of() : List.copyOf(projectHighlights);
			return updateProfile(userId, resumeId, profile -> new ResumeProfile(
					profile.resumeId(),
					profile.userId(),
					profile.mediaFileId(),
					profile.originalFileName(),
					profile.contentType(),
					profile.sizeBytes(),
					"PARSED",
					resumeSummary,
					keywords,
					highlights,
					null
			));
		}

		@Override
		public ResumeProfile markFailed(String userId, String resumeId, String parseError) {
			return updateProfile(userId, resumeId, profile -> new ResumeProfile(
					profile.resumeId(),
					profile.userId(),
					profile.mediaFileId(),
					profile.originalFileName(),
					profile.contentType(),
					profile.sizeBytes(),
					"FAILED",
					null,
					List.of(),
					List.of(),
					parseError
			));
		}

		private ResumeProfile updateProfile(String userId, String resumeId,
				java.util.function.UnaryOperator<ResumeProfile> updater) {
			ResumeProfile current = findByResumeId(userId, resumeId);
			ResumeProfile updated = updater.apply(current);
			profiles.put(updated.resumeId(), updated);
			return updated;
		}
	}

	private void validateRequired(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank");
		}
	}
}
