package com.interview.module.interview.service;

import java.util.ArrayList;
import java.util.List;

import com.interview.module.interview.engine.model.InterviewQuestionSource;

public class InterviewQuestionComposer {

	private final List<CandidateQuestion> resumeCandidates;
	private final List<CandidateQuestion> libraryCandidates;

	private InterviewQuestionComposer(List<CandidateQuestion> resumeCandidates, List<CandidateQuestion> libraryCandidates) {
		this.resumeCandidates = resumeCandidates == null ? List.of() : List.copyOf(resumeCandidates);
		this.libraryCandidates = libraryCandidates == null ? List.of() : List.copyOf(libraryCandidates);
	}

	public static InterviewQuestionComposer inMemory(
			List<CandidateQuestion> resumeCandidates,
			List<CandidateQuestion> libraryCandidates
	) {
		return new InterviewQuestionComposer(resumeCandidates, libraryCandidates);
	}

	public List<ComposedQuestion> compose(String stage, List<String> resumeKeywords, List<String> presetKeys) {
		List<ComposedQuestion> result = new ArrayList<>();
		result.addAll(resumeCandidates.stream()
				.map(question -> new ComposedQuestion(question.title(), question.prompt(), question.source(), stage))
				.toList());
		result.addAll(libraryCandidates.stream()
				.map(question -> new ComposedQuestion(question.title(), question.prompt(), question.source(), stage))
				.toList());
		if (result.isEmpty()) {
			result.add(new ComposedQuestion(
					"自我介绍",
					"请用 1 分钟介绍最近两年的 Java 后端经历。",
					InterviewQuestionSource.PRESET.name(),
					stage
			));
		}
		return result;
	}

	public record CandidateQuestion(
			String title,
			String prompt,
			String source
	) {
	}

	public record ComposedQuestion(
			String title,
			String prompt,
			String source,
			String stage
	) {
	}
}
