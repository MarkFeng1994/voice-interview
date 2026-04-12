# 模拟面试报告解释能力设计

**日期**: 2026-04-11

**范围**: 本阶段只覆盖 `voice-interview-mobile` 的报告页解释能力增强，以及其依赖的后端报告解释生成链路；不覆盖面试过程页解释展示，不覆盖 `voice-interview-admin`，不引入 RAG / agent runtime。

## 1. 背景

当前项目已经完成了“面试流程与追问策略增强”第一阶段：

- 题目快照已保留 `sourceSnapshot` / `difficultySnapshot`
- 回答分析已升级为结构化 `AnswerEvidence`
- 追问决策已通过 `FollowUpDecisionEngine` 走后端规则
- 轮次记录已保留：
  - `analysisReason`
  - `followUpDecision`
  - `followUpDecisionReason`
  - `missingPointsSnapshot`

但当前 mobile 报告页仍主要展示：

- 总评分
- 总体评价 `overallComment`
- 优势 / 薄弱项 / 建议
- 分题摘要 `questionReports.summary`

这意味着后端已经积累了足够的结构化诊断信息，但报告页还不能清楚回答：

- 为什么整体表现被判成当前水平
- 每道题到底是“缺关键点”“深度不足”还是“答偏了”
- 哪些结论来自规则判断，哪些来自 AI 润色

本阶段的目标，是把已经存在的结构化分析数据转换成“可解释、可展示、可降级”的报告解释能力。

## 2. 目标

- 在 `voice-interview-mobile` 报告页新增“总评解释”和“分题解释”
- 采用“规则打底 + LLM 润色”的生成链路
- 在 LLM 不可用时，仍然返回可直接展示的规则文案
- 让总评层和分题层都能给出：
  - 结论
  - 证据点
  - 改进建议
- 保持现有报告页入口、路由和基础信息结构稳定，不要求用户学习新页面

## 3. 非目标

本阶段不做以下内容：

- 不在面试会话页展示“为什么被追问/为什么没追问”
- 不修改 `voice-interview-admin`
- 不引入向量检索、知识库或 agent runtime
- 不让 LLM 直接从原始轮次自由生成最终报告
- 不做报告分享、导出 PDF、历史对比图表

## 4. 总体方案

本阶段采用三层解释生成方案：

1. **规则解释层**
   - 基于现有报告数据和轮次分析数据生成结构化解释对象
   - 输出稳定字段，作为前端展示与 LLM 降级兜底基础

2. **润色生成层**
   - 将规则解释对象喂给 AI 做文案润色
   - 只负责“表达更自然”，不负责改写结论和证据边界

3. **前端展示层**
   - mobile 报告页优先展示润色文案
   - 若润色失败，则回退展示规则解释文案
   - 同时保留结构化字段，用于标签、证据点、建议块渲染

核心原则：

- 结论来自规则，不来自 LLM
- LLM 只做润色，不做裁决
- LLM 失败时，页面仍有完整解释

## 5. 用户确认的产品决策

- 前端范围：仅 `voice-interview-mobile`
- 展示层级：`总评 + 分题回顾` 都增强
- 展示风格：`诊断型`
- 生成策略：`规则打底 + LLM 润色`
- 降级方式：`LLM 失败时回退到规则文案`

## 6. 后端数据模型设计

### 6.1 总评解释对象

建议在报告模型中新增：

- `overallExplanation`

结构示例：

```json
{
  "level": "STRONG",
  "summaryText": "本轮整体回答结构较完整，但在高价值题的细节展开上仍有提升空间。",
  "evidencePoints": [
    "大多数题目都能覆盖核心点",
    "项目题在一致性和权衡表达上较稳定",
    "部分追问场景下细节展开仍偏浅"
  ],
  "improvementSuggestions": [
    "重点补强项目题里的过程、取舍和结果表达",
    "准备 2 到 3 个高频项目追问案例"
  ],
  "generatedBy": "RULE_PLUS_LLM"
}
```

字段含义：

- `level`: `STRONG / MEDIUM / WEAK`
- `summaryText`: 总评结论
- `evidencePoints`: 2 到 3 条证据点
- `improvementSuggestions`: 1 到 2 条建议
- `generatedBy`: `RULE` 或 `RULE_PLUS_LLM`

### 6.2 分题解释对象

建议在每道题的报告对象中新增：

- `explanation`

结构示例：

```json
{
  "performanceLevel": "MEDIUM",
  "summaryText": "这题回答到了 Redis 的核心用途，但一致性策略没有展开。",
  "evidencePoints": [
    "覆盖了缓存场景",
    "缺少一致性策略说明"
  ],
  "improvementSuggestion": "补充缓存与数据库一致性的处理方式，以及为什么这样设计。",
  "generatedBy": "RULE_PLUS_LLM"
}
```

字段含义：

- `performanceLevel`: `STRONG / MEDIUM / WEAK`
- `summaryText`: 该题结论
- `evidencePoints`: 1 到 2 条关键证据
- `improvementSuggestion`: 1 条建议
- `generatedBy`: `RULE` 或 `RULE_PLUS_LLM`

## 7. 后端生成链路设计

### 7.1 规则解释输入

本阶段不重新做大规模语义分析，而是复用现有报告和轮次结构化数据：

- `overallScore`
- `questionReports`
- `analysisReason`
- `followUpDecision`
- `followUpDecisionReason`
- `missingPointsSnapshot`
- 每题追问次数
- 每题最终得分

### 7.2 规则解释生成规则

总评规则解释建议按以下维度聚合：

- 是否普遍存在关键点缺失
- 是否存在多题深度不足
- 高价值题是否频繁被追问
- 最终总体分数落在哪个区间

分题规则解释建议按以下优先级生成：

1. 若存在 `missingPointsSnapshot`
   - 解释为“关键点缺失”
2. 若该题有多次追问且仍未拉高分数
   - 解释为“深度或稳定性不足”
3. 若存在明显答偏 / 矛盾原因
   - 解释为“回答与题目核心不一致”或“前后说法不一致”
4. 若得分较高且追问少
   - 解释为“回答较完整”

### 7.3 LLM 润色策略

LLM 输入不直接喂原始面试全文，而只喂：

- 规则生成的总评解释对象
- 规则生成的分题解释对象
- 题目标题与 prompt

LLM 任务仅包括：

- 压缩成更自然的 mobile 展示文案
- 调整语气，使之更像“诊断型报告”
- 保持结论和证据边界不变

LLM 不允许：

- 改变 `performanceLevel`
- 发明不存在的缺失点
- 改写规则结论

## 8. 前端展示设计

### 8.1 页面位置

仅增强现有页面：

- [report.vue](E:/developSoft/ideaworkspace/voice-interview/voice-interview-mobile/src/pages/interview/report.vue)

不新增新页面，不改历史页入口。

### 8.2 页面结构调整

在现有报告页中，按如下顺序展示：

1. Hero 区与总分
2. **总评解释卡**
3. 优势领域
4. 待加强
5. 下一步建议
6. **分题解释卡列表**

### 8.3 总评解释卡

新增区块：

- 标题：`为什么是这个结论`
- 内容：
  - `summaryText`
  - `evidencePoints`
  - `improvementSuggestions`
  - 小标签：`规则生成` / `AI 润色`

### 8.4 分题解释卡

升级现有“题目明细”卡：

- 保留：
  - 题号
  - 标题
  - 分数
- 增加：
  - `performanceLevel`
  - `summaryText`
  - `evidencePoints`
  - `improvementSuggestion`
  - 小标签：`规则生成` / `AI 润色`

### 8.5 视觉原则

这次不重做大版式，只做“信息密度增强”：

- 复用现有 card 结构
- 新增解释卡时保持阅读节奏一致
- `generatedBy` 只用轻量标签，不抢主信息
- 文案块默认折叠不需要，引导用户自然向下阅读

## 9. 降级与错误处理

### 9.1 后端降级

- 规则解释生成失败：
  - 回退到现有 `overallComment / questionReports.summary`
- LLM 润色失败：
  - 返回规则解释对象
  - `generatedBy = RULE`

### 9.2 前端降级

- 若新字段不存在：
  - 页面继续展示旧报告内容
- 若总评解释缺失：
  - 隐藏总评解释卡
- 若分题解释缺失：
  - 继续展示原来的题目摘要

### 9.3 约束

- `/api/interviews/{sessionId}/report` 不能因解释增强返回 500
- 最差结果只能退回旧报告表现，不能整页无法使用

## 10. 代码边界设计

### 10.1 后端

建议新增或扩展：

- 报告解释模型：
  - `InterviewOverallExplanationView`
  - `InterviewQuestionExplanationView`
- 报告解释生成服务：
  - `InterviewReportExplanationService`

建议扩展：

- `InterviewReportView`
- `InterviewQuestionReportView`
- `SimpleInterviewEngine#toReportView(...)`
- `AiService`
- `OpenAiCompatibleAiService`
- `LangChain4jAiService`

### 10.2 mobile

建议扩展：

- `voice-interview-mobile/src/types/interview.ts`
- `voice-interview-mobile/src/pages/interview/report.vue`

## 11. 测试与验证

### 11.1 后端测试

需要覆盖：

- 总评规则解释生成
- 分题规则解释生成
- LLM 润色失败回退
- 报告接口兼容旧字段

### 11.2 mobile 验证

需要覆盖：

- 报告页能展示总评解释卡
- 报告页能展示分题解释卡
- 新字段缺失时仍能正常展示旧内容
- 长文案不会破坏布局

### 11.3 回归验证

需确认：

- 历史页进入报告页不回归
- 面试结束后自动跳转报告不回归
- 没有 `sessionId` 时仍保留当前错误提示逻辑

## 12. 验收标准

本阶段验收通过需要满足：

- mobile 报告页可展示总评解释
- mobile 报告页可展示分题解释
- 解释风格符合“诊断型”
- LLM 失败时，仍能展示规则解释
- 旧报告核心内容不回归
- 报告接口保持可用

## 13. 推荐实施顺序

建议拆成 3 个实现任务：

1. 先补后端结构化解释模型与规则解释生成
2. 再补 LLM 润色与降级兜底
3. 最后接入 `voice-interview-mobile` 报告页展示

这样可以让：

- 数据模型先稳定
- 润色逻辑后置
- 前端不必反复适配中间态字段
