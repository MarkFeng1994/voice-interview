package com.interview.module.interview.service;

import java.util.ArrayList;
import java.util.List;

import com.interview.module.interview.engine.model.InterviewQuestionSource;

public class InterviewQuestionComposer {

	private final List<CandidateQuestion> resumeCandidates;
	private final List<CandidateQuestion> libraryCandidates;
	private final List<CandidateQuestion> presetCandidates;

	private InterviewQuestionComposer(
			List<CandidateQuestion> resumeCandidates,
			List<CandidateQuestion> libraryCandidates,
			List<CandidateQuestion> presetCandidates
	) {
		this.resumeCandidates = resumeCandidates == null ? List.of() : List.copyOf(resumeCandidates);
		this.libraryCandidates = libraryCandidates == null ? List.of() : List.copyOf(libraryCandidates);
		this.presetCandidates = presetCandidates == null ? List.of() : List.copyOf(presetCandidates);
	}

	public static InterviewQuestionComposer inMemory(
			List<CandidateQuestion> resumeCandidates,
			List<CandidateQuestion> libraryCandidates
	) {
		return new InterviewQuestionComposer(resumeCandidates, libraryCandidates, List.of());
	}

	public static InterviewQuestionComposer inMemory(
			List<CandidateQuestion> resumeCandidates,
			List<CandidateQuestion> libraryCandidates,
			List<CandidateQuestion> presetCandidates
	) {
		return new InterviewQuestionComposer(resumeCandidates, libraryCandidates, presetCandidates);
	}

	public List<ComposedQuestion> compose(String stage, List<String> resumeKeywords, List<String> presetKeys) {
		List<ComposedQuestion> result = new ArrayList<>();
		result.addAll(resumeCandidates.stream()
				.map(question -> toComposedQuestion(question, stage))
				.toList());
		result.addAll(libraryCandidates.stream()
				.map(question -> toComposedQuestion(question, stage))
				.toList());
		result.addAll(presetCandidates.stream()
				.map(question -> toComposedQuestion(question, stage))
				.toList());
		if (result.isEmpty()) {
			result.add(new ComposedQuestion(
					"自我介绍",
					"请用 1 分钟介绍最近两年的 Java 后端经历。",
					InterviewQuestionSource.PRESET.name(),
					stage,
					null,
					null,
					null
			));
		}
		return result;
	}

	private ComposedQuestion toComposedQuestion(CandidateQuestion question, String stage) {
		return new ComposedQuestion(
				question.title(),
				question.prompt(),
				question.source(),
				stage,
				question.sourceQuestionId(),
				question.sourceCategoryId(),
				question.difficulty()
		);
	}

	public record CandidateQuestion(
			String title,
			String prompt,
			String source,
			String sourceQuestionId,
			String sourceCategoryId,
			Integer difficulty
	) {
		public CandidateQuestion(String title, String prompt, String source) {
			this(title, prompt, source, null, null, null);
		}
	}

	public record ComposedQuestion(
			String title,
			String prompt,
			String source,
			String stage,
			String sourceQuestionId,
			String sourceCategoryId,
			Integer difficulty
	) {
		public ComposedQuestion(String title, String prompt, String source, String stage) {
			this(title, prompt, source, stage, null, null, null);
		}
	}
}
