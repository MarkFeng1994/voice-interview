package com.interview.module.interview.websocket;

import com.interview.module.interview.engine.model.InterviewSessionView;

public record InterviewSessionUpdatedEvent(
		String userId,
		InterviewSessionView session
) {
}
