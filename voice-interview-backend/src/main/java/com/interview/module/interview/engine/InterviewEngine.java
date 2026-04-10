package com.interview.module.interview.engine;

import java.util.List;

import com.interview.module.interview.engine.model.InterviewQuestionCard;
import com.interview.module.interview.engine.model.InterviewReportView;
import com.interview.module.interview.engine.model.InterviewSessionOwner;
import com.interview.module.interview.engine.model.InterviewSessionSummaryView;
import com.interview.module.interview.engine.model.InterviewSessionView;

public interface InterviewEngine {

	InterviewSessionView startSession(
			List<InterviewQuestionCard> questions,
			int maxFollowUpPerQuestion,
			InterviewSessionOwner owner,
			Integer interviewerSpeakerId,
			Double interviewerSpeechSpeed
	);

	InterviewSessionView getState(String sessionId, String requesterUserId);

	InterviewSessionView answer(String sessionId, String requesterUserId, String answerMode, String userText, String userAudioUrl);

	InterviewSessionView skip(String sessionId, String requesterUserId);

	InterviewSessionView end(String sessionId, String requesterUserId);

	List<InterviewSessionSummaryView> listSessions(String requesterUserId);

	InterviewReportView getReport(String sessionId, String requesterUserId);
}
