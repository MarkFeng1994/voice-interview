package com.interview.module.interview.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.interview.module.interview.engine.model.InterviewQuestionSnapshot;
import com.interview.module.interview.engine.model.InterviewSessionView;
import com.interview.module.interview.engine.model.InterviewStage;
import com.interview.module.interview.engine.store.InterviewSessionState;
import com.interview.module.interview.engine.store.InterviewSessionStore;

@Component
public class RealtimeInterviewStateMachine {

	private static final Logger log = LoggerFactory.getLogger(RealtimeInterviewStateMachine.class);

	private final FollowUpDecisionEngine decisionEngine;
	private final InterviewSessionStore sessionStore;

	public RealtimeInterviewStateMachine(
			FollowUpDecisionEngine decisionEngine,
			InterviewSessionStore sessionStore
	) {
		this.decisionEngine = decisionEngine;
		this.sessionStore = sessionStore;
	}

	public TurnResult processTurnComplete(
			InterviewSessionState state,
			String userAnswer,
			String aiReply
	) {
		state.appendRealtimeUserAnswer(userAnswer);

		InterviewQuestionSnapshot currentQ = state.getCurrentQuestion();
		AnswerEvidence evidence = InterviewAnswerAnalyzer.heuristic().analyze(
				currentQ != null ? currentQ.promptSnapshot() : "",
				userAnswer,
				null
		);

		FollowUpDecision decision = decisionEngine.decide(
				currentQ,
				state.getStage(),
				state.getFollowUpIndex(),
				state.getMaxFollowUpPerQuestion(),
				evidence
		);

		log.debug("Turn complete: session={}, action={}, reason={}",
				state.getSessionId(), decision.action(), decision.reasonCode());

		switch (decision.action()) {
			case FOLLOW_UP -> {
				state.setFollowUpIndex(state.getFollowUpIndex() + 1);
				state.appendRealtimeAiReply(aiReply, evidence, decision);
				sessionStore.save(state);
				return new TurnResult(TurnAction.CONTINUE, null, state.toView());
			}
			case NEXT_QUESTION -> {
				state.appendRealtimeAiReply(aiReply, evidence, decision);
				int nextIndex = state.getCurrentQuestionIndex() + 1;
				if (nextIndex >= state.getQuestions().size()) {
					return processComplete(state);
				}
				state.setCurrentQuestionIndex(nextIndex);
				state.setFollowUpIndex(0);
				state.setStage(resolveStage(state).name());
				sessionStore.save(state);
				return new TurnResult(
						TurnAction.UPDATE_PROMPT,
						RealtimeSystemPromptBuilder.build(state),
						state.toView()
				);
			}
			case END_INTERVIEW -> {
				return processComplete(state);
			}
		}

		throw new IllegalStateException("Unknown action: " + decision.action());
	}

	private TurnResult processComplete(InterviewSessionState state) {
		state.setStatus("COMPLETED");
		state.setStage(InterviewStage.WRAP_UP.name());
		sessionStore.save(state);
		return new TurnResult(TurnAction.UPDATE_PROMPT, RealtimeSystemPromptBuilder.buildWrapUp(), state.toView());
	}

	private InterviewStage resolveStage(InterviewSessionState state) {
		int idx = state.getCurrentQuestionIndex();
		int total = state.getQuestions().size();
		if (idx == 0) return InterviewStage.OPENING;
		if (idx <= Math.max(1, total / 2)) return InterviewStage.JAVA_CORE;
		return InterviewStage.PROJECT_DEEP_DIVE;
	}

	public record TurnResult(
			TurnAction action,
			String newSystemPrompt,
			InterviewSessionView view
	) {
	}

	public enum TurnAction {
		CONTINUE,
		UPDATE_PROMPT,
		DISCONNECT
	}
}
