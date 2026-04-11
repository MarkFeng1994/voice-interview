package com.interview.module.ai.service;

import java.util.List;

import com.interview.module.interview.service.AnswerEvidence;
import com.interview.module.interview.service.InterviewAnswerAnalyzer;
import com.interview.module.interview.resume.GeneratedResumeQuestion;
import com.interview.module.interview.resume.ResumeKeywordExtractionResult;
import com.interview.module.interview.resume.ResumeQuestionGenerationCommand;

public interface AiService {

	AiReply generateInterviewReply(InterviewReplyCommand command);

	ResumeKeywordExtractionResult extractResumeKeywords(String resumeText);

	List<GeneratedResumeQuestion> generateResumeQuestions(ResumeQuestionGenerationCommand command);

	default AnswerEvidence analyzeInterviewAnswer(
			String question,
			String answer,
			List<String> expectedPoints
	) {
		return InterviewAnswerAnalyzer.heuristic().analyze(question, answer, expectedPoints);
	}

	default InterviewReportExplanationResult polishInterviewReportExplanation(InterviewReportExplanationCommand command) {
		return null;
	}
}
