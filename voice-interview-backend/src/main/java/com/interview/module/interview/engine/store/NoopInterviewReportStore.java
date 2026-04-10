package com.interview.module.interview.engine.store;

import java.util.Optional;

import com.interview.module.interview.engine.model.InterviewReportView;

public class NoopInterviewReportStore implements InterviewReportStore {

	@Override
	public Optional<InterviewReportView> findBySessionId(String sessionId) {
		return Optional.empty();
	}

	@Override
	public void save(InterviewReportView report) {
		// No-op for non-persistent interview session modes.
	}
}
