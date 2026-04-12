# 历史报告 explanation 懒回填设计

**日期**: 2026-04-12

**范围**: 本阶段只覆盖 `voice-interview-backend` 中已持久化历史报告的 explanation 补齐能力，目标是在读取已持久化、且状态不是 `IN_PROGRESS` 的旧报告时按需补全 `overallExplanation` 与 `questionReports[].explanation`，并回写为新版本报告；不修改接口路径，不引入 LLM，不做批处理重算。

## 1. 背景

当前项目已经具备“报告解释能力”：

- 新生成报告会在 `SimpleInterviewEngine#toReportView(...)` 里调用 `InterviewReportExplanationService`
- 解释对象已经进入 `InterviewReportView`
- JDBC 报告持久化已经保存完整 `reportJson`

但这只覆盖“新生成的报告”。在 explanation 能力上线前已经持久化的历史报告，仍然存在以下问题：

- `overallExplanation` 为空
- `questionReports[].explanation` 为空
- mobile 报告页打开历史记录时，解释区块会缺失或只能退回旧摘要

现有 JDBC 存储层还保留了：

- 会话快照 `SessionQuestionEntity`
- 轮次记录 `RoundEntity`
- 报告版本字段 `ReportEntity.reportVersion`

这意味着项目已经具备“在读取旧报告时恢复上下文并补 explanation”的基础条件，不需要引入离线脚本或全量重算任务。

## 2. 目标

- 对已持久化、且状态不是 `IN_PROGRESS` 的历史 report 提供 explanation 懒回填
- 回填内容仅限：
  - `overallExplanation`
  - `questionReports[].explanation`
- 回填成功后回写 `reportJson`，并把 `reportVersion` 从 `v1` 升级到 `v2`
- 对调用方保持 `GET /api/interviews/{sessionId}/report` 接口不变
- 在回填失败时返回旧报告，不因为 explanation 补齐导致接口 500

## 3. 非目标

本阶段明确不做：

- 不对历史报告做全量重算
- 不重算 `overallScore`
- 不重算 `overallComment`
- 不重算 `strengths / weaknesses / suggestions`
- 不重算 `questionReports.summary`
- 不补批处理脚本、不做后台迁移任务
- 不引入 LLM 回填，不调用 AI 润色链路
- 不修改 mobile / admin 页面交互

## 4. 方案对比

### 方案 A：启动批处理脚本全量回填

优点：

- 可以一次性清完历史数据
- 线上读路径保持最干净

缺点：

- 需要额外脚本、执行窗口、失败重试与回滚策略
- 当前阶段只补 explanation，批处理的运维成本明显偏高
- 对开发和验证节奏不友好

### 方案 B：Engine 读路径懒回填

优点：

- 只在真正访问历史报告时补算，改动集中
- 不需要额外运维动作
- 能复用当前 `GET report` 的用户流

缺点：

- 第一次读取旧报告会多一次补算与回写
- 读路径需要承担版本判断和降级逻辑

### 方案 C：Store 层静默重写旧报告

优点：

- 控制点集中在持久化层
- 上层调用方改动较少

缺点：

- `JdbcInterviewReportStore` 当前只拿到 `reportJson`，拿不到回填 explanation 所需的会话上下文
- 若把恢复 questions / rounds 的职责塞进 store，会破坏现有边界

## 5. 推荐方案

采用 **方案 B：Engine 读路径懒回填**。

原因：

- explanation 生成依赖 `questions` 与 `rounds`，这些上下文天然属于 `SimpleInterviewEngine#getReport(...)` 读取流程，更适合在 engine 层拼装
- `InterviewReportExplanationService` 已经具备规则解释能力，可以直接复用
- 当前目标只补 explanation，不值得引入额外批处理体系

## 6. 设计原则

- **只补 explanation，不改历史评分语义**
- **优先按内容判断是否需要回填，再参考版本号**
- **规则回填，不走 LLM**
- **回填失败只记日志并返回旧 report**
- **不改变现有 API 契约**

## 7. 版本策略

### 7.1 版本定义

- 现状：`JdbcInterviewReportStore` 默认写入 `REPORT_VERSION = "v1"`
- 新阶段：把 explanation 回填后的持久化版本定义为 `v2`

### 7.2 判定原则

是否需要回填，优先按“内容缺失”判断：

- `overallExplanation == null`
- 或任一 `questionReports[].explanation == null`

`reportVersion` 作为辅助判断：

- `reportVersion == null`
- 或 `reportVersion != "v2"`

设计原因：

- 历史脏数据未必可靠维护了版本号
- 单看版本号容易漏掉“版本已写新值，但 explanation 实际缺失”的异常情况

### 7.3 升级时机

仅当 explanation 回填成功并完成持久化时，才把 `reportVersion` 升级为 `v2`。

如果回填失败：

- 不更新 `reportJson`
- 不升级版本号
- 保持旧记录原样返回

## 8. 核心流程

### 8.1 触发入口

保持现有接口入口不变：

- `GET /api/interviews/{sessionId}/report`

对应主流程仍位于：

- `SimpleInterviewEngine#getReport(...)`

### 8.2 读取路径行为

建议将已完成报告的读取流程调整为：

1. 先根据 `sessionId` 加载 `InterviewSessionState`
2. 若会话仍是 `IN_PROGRESS`
   - 直接返回当前内存态/实时态 report
   - 不做历史回填
3. 若会话状态不是 `IN_PROGRESS`
   - 从 `InterviewReportStore` 读取持久化 report 以及其版本信息
4. 判断该 report 是否缺 explanation
5. 若 explanation 完整
   - 直接返回
6. 若 explanation 缺失
   - 基于当前持久化 session / questions / rounds 恢复上下文
   - 调用 explanation 规则服务补 explanation
   - 持久化更新 `reportJson` + `reportVersion = v2`
   - 返回补完后的 report
7. 若补算或回写失败
   - 记录 warn 日志
   - 直接返回旧 report

### 8.3 为什么放在 Engine 而不是 Store

- engine 已经负责会话 ownership 校验
- engine 已经能拿到 `InterviewSessionState`
- explanation 生成需要 question snapshots 与 rounds
- store 更适合负责“存/取”，不适合承接解释生成编排

## 9. 回填生成策略

### 9.1 输入来源

回填 explanation 只使用已有持久化数据：

- `InterviewSessionState.questions`
- `InterviewSessionState.rounds`
- 旧 `InterviewReportView`

### 9.2 生成方式

复用现有 `InterviewReportExplanationService` 的**规则解释能力**：

- 允许使用：
  - `buildOverallExplanation(...)`
  - `buildQuestionExplanation(...)`
  - 或在 service 内新增“只做规则回填”的公开入口
- 不允许调用：
  - LLM 润色链路
  - `AiService.polishInterviewReportExplanation(...)`

### 9.3 输出策略

基于旧 report 生成一个“只补 explanation 的新 report”：

- 保留旧 report 原有字段值
- 仅写入：
  - `overallExplanation`
  - `questionReports[].explanation`

这样可以保证：

- 旧评分、旧总结口径不被改写
- 回填行为可控，影响范围收敛

## 10. 代码边界建议

### 10.1 Engine 层

建议在 `SimpleInterviewEngine` 增加“读取旧报告并按需回填”的编排逻辑，职责包括：

- 判断是否需要回填
- 从 session state 取 explanations 所需上下文
- 调用 explanation 规则服务
- 在成功后触发落库升级
- 在失败时做日志降级

### 10.2 Store 层

现有 `InterviewReportStore` 只暴露：

- `findBySessionId(String sessionId)`
- `save(InterviewReportView report)`

这不足以支撑“读取 reportVersion 并在成功回填后升级版本”。

建议新增一个面向 JDBC 持久化的读模型，例如：

- 返回 `report + reportVersion` 的包装对象
- 或新增 `findPersistedReportBySessionId(...)`

设计目标不是把 explanation 逻辑下沉到 store，而是让 engine 能拿到足够的版本元信息。

### 10.3 Explanation Service

建议把“规则解释生成”和“LLM 润色 enrich”边界显式拆开：

- 新报告生成：
  - 继续走现有 enrich 流程
- 历史报告回填：
  - 只走规则 explanation 流程

这样可以避免“为了回填 explanation，不小心触发 LLM”。

## 11. 降级与异常处理

### 11.1 必须降级返回旧 report 的场景

- 旧 report 反序列化成功，但 session questions 缺失
- rounds 缺失或结构不足以支撑 explanation 构建
- explanation service 规则回填抛异常
- 回写数据库失败

### 11.2 降级行为

统一策略：

- 记录 `warn` 级别日志，包含 `sessionId`
- 不抛 500
- 返回数据库中原有 report

### 11.3 日志要求

日志至少能区分：

- `report explanation backfill skipped`
- `report explanation backfill succeeded`
- `report explanation backfill failed`

便于后续排查历史数据质量与懒回填命中率。

## 12. 测试设计

### 12.1 单元 / 集成测试重点

至少覆盖以下场景：

1. 旧 report 首次读取时会触发 explanation 懒回填
2. 回填成功后会保存为 `v2`
3. 已有完整 explanation 的 report 不会重复回填
4. `reportVersion` 已是 `v2` 但 explanation 缺失时，仍会按内容缺失触发回填
5. session questions / rounds 不完整时，安全降级返回旧 report
6. 数据库回写失败时，安全降级返回旧 report
7. 回填过程不调用 LLM

### 12.2 回归验证

需要确认：

- 新生成 report 的现有 explanation 流程不回归
- `IN_PROGRESS` 会话读取报告逻辑不回归
- mobile 历史报告页读取接口返回结构保持兼容

## 13. 验收标准

本阶段通过验收需要满足：

- 历史 completed report 第一次被读取时，可按需补齐 explanation
- 只补 explanation，不改旧 report 的分数与其他总结字段
- 回填成功后持久化版本升级到 `v2`
- explanation 回填不走 LLM
- 任一回填失败场景都不会导致 `/api/interviews/{sessionId}/report` 返回 500
- 已经补齐 explanation 的 report 后续读取不重复回填

## 14. 实施边界结论

本阶段的最小闭环是：

- 在 engine 读路径增加旧报告 explanation 懒回填
- 在 store 层补足版本元信息读写能力
- 在 explanation service 层暴露纯规则回填入口
- 用测试锁定“只补 explanation、失败降级、版本升级到 v2”这三个核心约束

这保证下一步实现计划可以保持聚焦，不会扩散成历史报告全量重算工程。
