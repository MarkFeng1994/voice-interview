package com.interview.module.interview.engine.store;

import java.util.Optional;

import com.interview.module.interview.engine.model.InterviewReportView;

public interface InterviewReportStore {

	Optional<InterviewReportView> findBySessionId(String sessionId);

	void save(InterviewReportView report);
}
