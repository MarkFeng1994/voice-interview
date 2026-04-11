package com.interview.module.interview.service;

import org.springframework.stereotype.Service;

import com.interview.module.interview.engine.model.InterviewQuestionSnapshot;

@Service
public class FollowUpDecisionEngine {

	private final InterviewFlowPolicy flowPolicy;

	public FollowUpDecisionEngine(InterviewFlowPolicy flowPolicy) {
		this.flowPolicy = flowPolicy;
	}

	public FollowUpDecision decide(
			InterviewQuestionSnapshot question,
			String stage,
			int followUpIndex,
			int sessionMaxFollowUp,
			AnswerEvidence evidence
	) {
		if ("WRAP_UP".equals(stage)) {
			return FollowUpDecision.nextQuestion("WRAP_UP_SKIP_FOLLOW_UP", "收尾阶段优先保持节奏");
		}
		boolean highValueQuestion = flowPolicy.isHighValueQuestion(stage, question);
		int followUpLimit = flowPolicy.resolveFollowUpLimit(stage, highValueQuestion, sessionMaxFollowUp);
		if (followUpIndex >= followUpLimit) {
			return FollowUpDecision.nextQuestion("FOLLOW_UP_LIMIT_REACHED", "已达到追问上限");
		}
		if (evidence.correctnessRisk() == AnswerEvidence.CorrectnessRisk.SUSPECTED_CONTRADICTION) {
			return FollowUpDecision.followUp("CLARIFY_CONTRADICTION", "CORRECTNESS_RISK", evidence.summaryReason());
		}
		if (evidence.correctnessRisk() == AnswerEvidence.CorrectnessRisk.CLEARLY_WRONG) {
			return FollowUpDecision.followUp("MISSING_KEY_POINT", "OFF_TOPIC_OR_WRONG", evidence.summaryReason());
		}
		if (evidence.completeness() == AnswerEvidence.Completeness.LOW) {
			return FollowUpDecision.followUp("MISSING_KEY_POINT", "LOW_COMPLETENESS", evidence.summaryReason());
		}
		if (evidence.completeness() == AnswerEvidence.Completeness.MEDIUM) {
			return FollowUpDecision.followUp("MISSING_KEY_POINT", "MEDIUM_COMPLETENESS", evidence.summaryReason());
		}
		if (highValueQuestion && evidence.depth() == AnswerEvidence.Depth.SHALLOW) {
			return FollowUpDecision.followUp("NEED_EXAMPLE_OR_DETAIL", "HIGH_VALUE_DEPTH_PROBE", evidence.summaryReason());
		}
		return FollowUpDecision.nextQuestion("ANSWER_GOOD_ENOUGH", "当前回答已达到继续下一题的标准");
	}
}
