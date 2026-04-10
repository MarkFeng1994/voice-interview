package com.interview.module.ai.service;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.interview.module.interview.resume.GeneratedResumeQuestion;
import com.interview.module.interview.resume.ResumeKeywordExtractionResult;
import com.interview.module.interview.resume.ResumeQuestionGenerationCommand;

@Service
@ConditionalOnProperty(prefix = "app.ai", name = "provider", havingValue = "mock")
public class MockAiService implements AiService {

	@Override
	public AiReply generateInterviewReply(InterviewReplyCommand command) {
		return new AiReply("好的，我们继续下一题。", "NEXT_QUESTION", 80);
	}

	@Override
	public ResumeKeywordExtractionResult extractResumeKeywords(String resumeText) {
		return new ResumeKeywordExtractionResult(
				"候选人具备 Java 后端开发经验。",
				List.of("Java", "Spring Boot", "MySQL"),
				List.of("具备后端项目交付经验", "熟悉常见中间件与数据库")
		);
	}

	@Override
	public List<GeneratedResumeQuestion> generateResumeQuestions(ResumeQuestionGenerationCommand command) {
		if (command.questionCount() <= 0) {
			return List.of();
		}
		return List.of(new GeneratedResumeQuestion(
				"项目亮点",
				"请介绍一个你最有代表性的后端项目，并说明你的核心职责。",
				command.missingKeywords().isEmpty() ? null : command.missingKeywords().get(0),
				2
		));
	}
}
