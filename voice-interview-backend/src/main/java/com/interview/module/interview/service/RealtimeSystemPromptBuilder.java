package com.interview.module.interview.service;

import com.interview.module.interview.engine.model.InterviewQuestionSnapshot;
import com.interview.module.interview.engine.model.InterviewStage;
import com.interview.module.interview.engine.store.InterviewSessionState;

public final class RealtimeSystemPromptBuilder {

	private RealtimeSystemPromptBuilder() {
	}

	public static String build(InterviewSessionState state) {
		InterviewQuestionSnapshot currentQ = state.getCurrentQuestion();
		InterviewStage stage = InterviewStage.valueOf(state.getStage());

		StringBuilder prompt = new StringBuilder();

		prompt.append("你是一位专业的 Java 技术面试官，正在进行一场实时语音面试。\n\n");

		prompt.append("## 当前阶段\n");
		prompt.append(switch (stage) {
			case OPENING -> "开场阶段 - 营造轻松氛围，建立信任";
			case JAVA_CORE -> "Java 核心技术考察 - 深入技术细节";
			case PROJECT_DEEP_DIVE -> "项目深挖阶段 - 结合实战经验";
			case WRAP_UP -> "收尾阶段 - 总结并鼓励候选人";
		});
		prompt.append("\n\n");

		if (stage != InterviewStage.WRAP_UP && currentQ != null) {
			prompt.append("## 当前题目\n");
			prompt.append("**题目**: ").append(currentQ.titleSnapshot()).append("\n");
			prompt.append("**考察点**: ").append(currentQ.promptSnapshot()).append("\n\n");

			int followUpIndex = state.getFollowUpIndex();
			int maxFollowUp = state.getMaxFollowUpPerQuestion();
			prompt.append("## 追问策略\n");
			prompt.append("当前已追问 ").append(followUpIndex)
					.append(" 次，最多 ").append(maxFollowUp).append(" 次。\n");
			prompt.append("根据候选人回答质量决定：\n");
			prompt.append("- 回答完整且深入 -> 进入下一题\n");
			prompt.append("- 回答浅显或遗漏要点 -> 追问细节\n");
			prompt.append("- 回答明显错误 -> 引导纠正\n\n");
		} else {
			prompt.append("## 面试结束\n");
			prompt.append("请用 1-2 句话自然地结束面试，鼓励候选人，不要过于冗长。\n\n");
		}

		prompt.append("## 对话风格\n");
		prompt.append("- 语速适中，吐字清晰\n");
		prompt.append("- 语气专业但友好，避免生硬\n");
		prompt.append("- 回复简洁，每次 2-3 句话即可\n");
		prompt.append("- 允许候选人随时打断你\n");

		return prompt.toString();
	}

	public static String buildWrapUp() {
		return """
				你是一位专业的 Java 技术面试官。面试已经结束。

				## 面试结束
				请用 1-2 句话自然地结束面试，鼓励候选人，不要过于冗长。

				## 对话风格
				- 语速适中，吐字清晰
				- 语气专业但友好，避免生硬
				- 回复简洁，每次 2-3 句话即可
				""";
	}
}
