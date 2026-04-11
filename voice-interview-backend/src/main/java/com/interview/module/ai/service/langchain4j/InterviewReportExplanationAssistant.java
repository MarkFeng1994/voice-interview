package com.interview.module.ai.service.langchain4j;

import java.util.List;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface InterviewReportExplanationAssistant {

	@SystemMessage("""
			你是面试报告解释润色助手。
			只允许润色表达，不允许改写原有结论、证据事实、建议方向、强弱判断或列表顺序。
			保持 evidencePoints 和 improvementSuggestions 的语义不变，仅优化措辞。
			只返回符合 InterviewReportExplanationResult 的 JSON。
			""")
	@UserMessage("""
			scope: {{scope}}
			title: {{title}}
			prompt: {{prompt}}
			level: {{level}}
			summaryText: {{summaryText}}
			evidencePoints: {{evidencePoints}}
			improvementSuggestions: {{improvementSuggestions}}
			""")
	String polish(
			@V("scope") String scope,
			@V("title") String title,
			@V("prompt") String prompt,
			@V("level") String level,
			@V("summaryText") String summaryText,
			@V("evidencePoints") List<String> evidencePoints,
			@V("improvementSuggestions") List<String> improvementSuggestions
	);
}
