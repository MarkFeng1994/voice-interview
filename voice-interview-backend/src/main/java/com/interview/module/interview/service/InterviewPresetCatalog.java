package com.interview.module.interview.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.interview.module.interview.engine.model.InterviewQuestionCard;

@Component
public class InterviewPresetCatalog {

	private final Map<String, PresetDefinition> presetsByKey = Map.of(
			"backend-core",
			new PresetDefinition(
					"backend-core",
					"后端核心能力",
					"聚焦 Java 后端、缓存、高并发和事务一致性。",
					List.of("Java", "高并发", "数据库"),
					List.of(
							new InterviewQuestionCard("自我介绍", "请做一个 1 分钟左右的自我介绍，重点说明你最近两年的后端项目经历。"),
							new InterviewQuestionCard("高并发系统", "请讲一个高并发活动系统案例，说明你如何处理限流、降级和热点数据。"),
							new InterviewQuestionCard("事务一致性", "如果把刚才的案例落到事务一致性和补偿机制上，你会如何设计边界？")
					)
			),
			"microservice-troubleshooting",
			new PresetDefinition(
					"microservice-troubleshooting",
					"微服务排障",
					"聚焦链路追踪、稳定性治理、线上问题定位。",
					List.of("微服务", "排障", "稳定性"),
					List.of(
							new InterviewQuestionCard("故障定位", "线上某个核心接口 RT 突增，你会如何快速定位是应用、数据库还是下游依赖的问题？"),
							new InterviewQuestionCard("服务治理", "如果调用链里有一个下游服务持续超时，你会如何做超时、重试、熔断和隔离？"),
							new InterviewQuestionCard("发布与回滚", "发布后出现少量错误但无法完全复现时，你如何判断是否回滚，以及如何缩小影响面？")
					)
			),
			"hr-communication",
			new PresetDefinition(
					"hr-communication",
					"表达与行为面",
					"聚焦项目表达、跨团队协作、复盘与成长。",
					List.of("行为面", "项目表达", "沟通"),
					List.of(
							new InterviewQuestionCard("项目亮点", "挑一个你最有代表性的项目，按背景、目标、动作、结果四段来讲。"),
							new InterviewQuestionCard("协作冲突", "遇到产品、测试或其他研发角色和你意见不一致时，你通常如何推进？"),
							new InterviewQuestionCard("复盘成长", "讲一个你做得不够好的项目决策，以及你后续是如何修正自己的。")
					)
			)
	);

	public List<InterviewPresetView> list() {
		return presetsByKey.values().stream()
				.map(preset -> new InterviewPresetView(
						preset.key(),
						preset.title(),
						preset.summary(),
						preset.tags(),
						preset.questions().size()
				))
				.sorted((left, right) -> left.title().compareTo(right.title()))
				.toList();
	}

	public PresetDefinition resolve(String presetKey) {
		if (presetKey == null || presetKey.isBlank()) {
			return presetsByKey.get("backend-core");
		}
		PresetDefinition preset = presetsByKey.get(presetKey.trim());
		if (preset == null) {
			throw new IllegalArgumentException("未知的面试预设: " + presetKey);
		}
		return preset;
	}

	public List<InterviewQuestionCard> listQuestionsByKeys(List<String> presetKeys) {
		if (presetKeys == null || presetKeys.isEmpty()) {
			return resolve(null).questions();
		}
		return presetKeys.stream()
				.map(this::resolve)
				.flatMap(preset -> preset.questions().stream())
				.toList();
	}

	public record InterviewPresetView(
			String key,
			String title,
			String summary,
			List<String> tags,
			int questionCount
	) {
	}

	public record PresetDefinition(
			String key,
			String title,
			String summary,
			List<String> tags,
			List<InterviewQuestionCard> questions
	) {
	}
}
