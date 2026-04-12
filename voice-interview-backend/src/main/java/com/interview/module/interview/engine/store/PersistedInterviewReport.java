package com.interview.module.interview.engine.store;

import com.interview.module.interview.engine.model.InterviewReportView;

public record PersistedInterviewReport(InterviewReportView report, String reportVersion) {

	public PersistedInterviewReport {
		if (reportVersion != null && reportVersion.isBlank()) {
			reportVersion = null;
		}
	}
}
