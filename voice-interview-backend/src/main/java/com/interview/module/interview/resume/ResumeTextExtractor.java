package com.interview.module.interview.resume;

import com.interview.module.media.service.StoredMediaFile;

public interface ResumeTextExtractor {

	ResumeText extract(StoredMediaFile storedMediaFile);

	record ResumeText(
			String plainText,
			int pageCount,
			int charCount
	) {
	}
}
