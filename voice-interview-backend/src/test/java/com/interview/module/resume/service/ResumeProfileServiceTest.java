package com.interview.module.resume.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumeProfileServiceTest {

	@Test
	void createPendingProfile_marks_status_uploaded() {
		ResumeProfileService service = ResumeProfileService.inMemory();

		ResumeProfile profile = service.createPendingProfile(
				"user-1",
				"file-1",
				"resume.pdf",
				"application/pdf",
				2048L
		);

		assertEquals("user-1", profile.userId());
		assertEquals("file-1", profile.mediaFileId());
		assertEquals("UPLOADED", profile.parseStatus());
		assertTrue(profile.extractedKeywords().isEmpty());
		assertTrue(profile.projectHighlights().isEmpty());
	}
}
