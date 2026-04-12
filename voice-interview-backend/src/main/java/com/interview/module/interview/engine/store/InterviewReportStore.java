package com.interview.module.interview.engine.store;

import java.util.Optional;

import com.interview.module.interview.engine.model.InterviewReportView;

public interface InterviewReportStore {

	Optional<PersistedInterviewReport> findPersistedReportBySessionId(String sessionId);

	default Optional<InterviewReportView> findBySessionId(String sessionId) {
		return findPersistedReportBySessionId(sessionId).map(PersistedInterviewReport::report);
	}

	default void save(InterviewReportView report) {
		save(report, "v2");
	}

	void save(InterviewReportView report, String reportVersion);
}
