package com.interview.module.interview.engine.store;

import java.util.Optional;
import java.util.List;

public interface InterviewSessionStore {

	void save(InterviewSessionState sessionState);

	Optional<InterviewSessionState> findById(String sessionId);

	List<InterviewSessionState> findAll();
}
