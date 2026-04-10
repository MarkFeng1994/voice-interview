package com.interview.module.ai.service.langchain4j;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface ResumeKeywordAssistant {

	@SystemMessage("""
			你是技术简历分析助手。
			只返回符合 ResumeKeywordOutput 的 JSON。
			""")
	@UserMessage("""
			resumeText:
			{{resumeText}}
			""")
	String extract(@V("resumeText") String resumeText);
}
