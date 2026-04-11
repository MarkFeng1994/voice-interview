package com.interview.module.interview.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Service;

import com.interview.module.interview.engine.model.InterviewOverallExplanationView;
import com.interview.module.interview.engine.model.InterviewQuestionExplanationView;
import com.interview.module.interview.engine.model.InterviewQuestionReportView;
import com.interview.module.interview.engine.model.InterviewQuestionSnapshot;
import com.interview.module.interview.engine.model.InterviewReportView;
import com.interview.module.interview.engine.model.InterviewRoundRecord;

@Service
public class InterviewReportExplanationService {

	public InterviewReportView enrichReport(
			InterviewReportView report,
			List<InterviewQuestionSnapshot> questions,
			List<InterviewRoundRecord> rounds
	) {
		if (report == null) {
			return null;
		}
		List<InterviewQuestionReportView> explainedQuestionReports = new ArrayList<>();
		for (InterviewQuestionReportView questionReport : safeQuestionReports(report.questionReports())) {
			InterviewQuestionExplanationView explanation = buildQuestionExplanation(
					findQuestion(questions, questionReport.questionIndex()),
					questionReport,
					roundsForQuestion(rounds, questionReport.questionIndex())
			);
			explainedQuestionReports.add(new InterviewQuestionReportView(
					questionReport.questionIndex(),
					questionReport.title(),
					questionReport.prompt(),
					questionReport.score(),
					questionReport.summary(),
					explanation
			));
		}
		return new InterviewReportView(
				report.sessionId(),
				report.status(),
				report.title(),
				report.overallScore(),
				report.overallComment(),
				report.strengths(),
				report.weaknesses(),
				report.suggestions(),
				List.copyOf(explainedQuestionReports),
				buildOverallExplanation(report.overallScore(), explainedQuestionReports, rounds)
		);
	}

	public InterviewOverallExplanationView buildOverallExplanation(
			Integer overallScore,
			List<InterviewQuestionReportView> questionReports,
			List<InterviewRoundRecord> rounds
	) {
		String level = levelFromScore(overallScore);
		int strongQuestions = 0;
		int weakQuestions = 0;
		for (InterviewQuestionReportView questionReport : safeQuestionReports(questionReports)) {
			if (questionReport.score() == null) {
				continue;
			}
			if (questionReport.score() >= 80) {
				strongQuestions++;
			} else if (questionReport.score() < 60) {
				weakQuestions++;
			}
		}

		int followUpCount = 0;
		int missingPointCount = 0;
		int riskCount = 0;
		for (InterviewRoundRecord round : safeRounds(rounds)) {
			if ("FOLLOW_UP".equals(round.followUpDecision())) {
				followUpCount++;
			}
			if (!round.missingPointsSnapshot().isEmpty()) {
				missingPointCount++;
			}
			if (containsRiskSignal(round.analysisReason()) || containsRiskSignal(round.followUpDecisionReason())) {
				riskCount++;
			}
		}

		String summaryText = switch (level) {
			case "STRONG" -> "整体表现较稳定，多数题目能给出完整回答，追问下也能基本站稳结论。";
			case "MEDIUM" -> "整体基础可用，但关键点覆盖和追问深度还不够稳定，部分题目仍需要继续补强。";
			default -> "整体表现偏弱，关键点覆盖、追问深度和答题稳定性都还有明显提升空间。";
		};

		LinkedHashSet<String> evidencePoints = new LinkedHashSet<>();
		if (strongQuestions > 0) {
			evidencePoints.add("有 " + strongQuestions + " 道题得分达到 80 分以上，说明部分题目已经具备较完整的表达。");
		}
		if (missingPointCount > 0) {
			evidencePoints.add("共有 " + missingPointCount + " 个答题轮次暴露出关键点缺失，回答覆盖度还不够稳定。");
		}
		if (followUpCount > 0) {
			evidencePoints.add("本轮触发了 " + followUpCount + " 次继续追问，说明部分题目需要靠补充说明才能站稳。");
		}
		if (riskCount > 0) {
			evidencePoints.add("有 " + riskCount + " 个轮次出现答偏或前后不一致风险信号，需要强化题意对齐。");
		}
		if (weakQuestions > 0) {
			evidencePoints.add("有 " + weakQuestions + " 道题得分低于 60 分，短板题还需要系统复盘。");
		}
		if (evidencePoints.isEmpty()) {
			evidencePoints.add("当前有效答题记录有限，总评主要依据现有得分和分题摘要生成。");
		}

		LinkedHashSet<String> improvementSuggestions = new LinkedHashSet<>();
		if (missingPointCount > 0) {
			improvementSuggestions.add("按题型整理每题必须覆盖的关键点，先保证回答完整度。");
		}
		if (followUpCount > 0 || weakQuestions > 0) {
			improvementSuggestions.add("高频题准备“背景、方案、取舍、结果”的固定表达，提升追问稳定性。");
		}
		if (riskCount > 0) {
			improvementSuggestions.add("先对齐题干再作答，避免用相近概念替代真正被问到的核心问题。");
		}
		if (improvementSuggestions.isEmpty()) {
			improvementSuggestions.add("继续保持高频复盘，把高分题沉淀成可复用表达模板。");
		}

		return new InterviewOverallExplanationView(
				level,
				summaryText,
				limit(evidencePoints, 3),
				limit(improvementSuggestions, 3),
				"RULE"
		);
	}

	public InterviewQuestionExplanationView buildQuestionExplanation(
			InterviewQuestionSnapshot question,
			InterviewQuestionReportView questionReport,
			List<InterviewRoundRecord> questionRounds
	) {
		String performanceLevel = levelFromScore(questionReport == null ? null : questionReport.score());
		LinkedHashSet<String> evidencePoints = new LinkedHashSet<>();
		LinkedHashSet<String> missingPoints = new LinkedHashSet<>();
		int followUpCount = 0;
		boolean hasRiskSignal = false;

		for (InterviewRoundRecord round : safeRounds(questionRounds)) {
			if ("FOLLOW_UP".equals(round.followUpDecision())) {
				followUpCount++;
			}
			if (round.analysisReason() != null && !round.analysisReason().isBlank()) {
				evidencePoints.add(round.analysisReason().trim());
			}
			if (!round.missingPointsSnapshot().isEmpty()) {
				missingPoints.addAll(round.missingPointsSnapshot());
			}
			if (containsRiskSignal(round.analysisReason()) || containsRiskSignal(round.followUpDecisionReason())) {
				hasRiskSignal = true;
			}
		}

		String questionTitle = question != null && question.titleSnapshot() != null && !question.titleSnapshot().isBlank()
				? question.titleSnapshot()
				: questionReport == null ? "当前题目" : questionReport.title();
		String summaryText;
		String improvementSuggestion;
		if (!missingPoints.isEmpty()) {
			String missingPointsText = String.join("、", missingPoints);
			evidencePoints.add("缺少关键点：" + missingPointsText);
			summaryText = questionTitle + " 这题还缺少对 " + missingPointsText + " 的说明，核心覆盖不够完整。";
			improvementSuggestion = "补充 " + missingPointsText + "，并明确你的方案、取舍和落地方式。";
		} else if (followUpCount >= 2 && (questionReport == null || questionReport.score() == null || questionReport.score() < 80)) {
			evidencePoints.add("同一题触发了 " + followUpCount + " 次继续追问，说明细节展开还不够稳定。");
			summaryText = questionTitle + " 这题基础结论已经给出，但面对连续追问时深度和细节还不够扎实。";
			improvementSuggestion = "围绕这题补一版完整案例，重点准备过程、权衡和结果。";
		} else if (hasRiskSignal) {
			evidencePoints.add("分析中出现了答偏或前后不一致风险信号。");
			summaryText = questionTitle + " 这题存在答偏风险，说明回答和题目核心的对齐度还不够稳定。";
			improvementSuggestion = "先拆清题干，再按“结论、依据、方案”顺序作答，避免偏离问题本身。";
		} else if (questionReport != null && questionReport.score() != null && questionReport.score() >= 80) {
			evidencePoints.add("得分较高且额外追问较少，说明回答完整度和稳定性都不错。");
			summaryText = questionTitle + " 这题回答较完整，核心点覆盖和表达稳定性都达到了较好水平。";
			improvementSuggestion = "保留现有表达结构，后续只需要继续补强案例细节。";
		} else {
			evidencePoints.add("当前回答覆盖了部分核心点，但深度和举例还可以继续补强。");
			summaryText = questionTitle + " 这题基础回答有了，但细节深度和案例支撑还不够充分。";
			improvementSuggestion = "补充更具体的案例和取舍说明，让回答从结论走到可落地方案。";
		}

		return new InterviewQuestionExplanationView(
				performanceLevel,
				summaryText,
				limit(evidencePoints, 3),
				improvementSuggestion,
				"RULE"
		);
	}

	private InterviewQuestionSnapshot findQuestion(List<InterviewQuestionSnapshot> questions, int questionIndex) {
		for (InterviewQuestionSnapshot question : questions == null ? List.<InterviewQuestionSnapshot>of() : questions) {
			if (question.questionIndex() == questionIndex) {
				return question;
			}
		}
		return null;
	}

	private List<InterviewRoundRecord> roundsForQuestion(List<InterviewRoundRecord> rounds, int questionIndex) {
		List<InterviewRoundRecord> questionRounds = new ArrayList<>();
		for (InterviewRoundRecord round : safeRounds(rounds)) {
			if (round.questionIndex() == questionIndex) {
				questionRounds.add(round);
			}
		}
		return questionRounds;
	}

	private List<InterviewRoundRecord> safeRounds(List<InterviewRoundRecord> rounds) {
		return rounds == null ? List.of() : rounds;
	}

	private List<InterviewQuestionReportView> safeQuestionReports(List<InterviewQuestionReportView> questionReports) {
		return questionReports == null ? List.of() : questionReports;
	}

	private String levelFromScore(Integer score) {
		if (score != null && score >= 80) {
			return "STRONG";
		}
		if (score != null && score < 60) {
			return "WEAK";
		}
		return "MEDIUM";
	}

	private boolean containsRiskSignal(String text) {
		if (text == null || text.isBlank()) {
			return false;
		}
		return text.contains("不一致")
				|| text.contains("答偏")
				|| text.contains("偏题")
				|| text.contains("不太一致")
				|| text.contains("风险");
	}

	private List<String> limit(LinkedHashSet<String> items, int maxSize) {
		List<String> limited = new ArrayList<>();
		for (String item : items) {
			if (item == null || item.isBlank()) {
				continue;
			}
			limited.add(item);
			if (limited.size() >= maxSize) {
				break;
			}
		}
		return List.copyOf(limited);
	}
}
