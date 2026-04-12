package com.interview.module.interview.engine.store;

import java.util.Optional;

import com.interview.module.interview.engine.model.InterviewReportView;

public interface InterviewReportStore {

	String LATEST_REPORT_VERSION = "v2";

	Optional<PersistedInterviewReport> findPersistedReportBySessionId(String sessionId);

	default Optional<InterviewReportView> findBySessionId(String sessionId) {
		return findPersistedReportBySessionId(sessionId).map(PersistedInterviewReport::report);
	}

	default void save(InterviewReportView report) {
		save(report, LATEST_REPORT_VERSION);
	}

	void save(InterviewReportView report, String reportVersion);
}
