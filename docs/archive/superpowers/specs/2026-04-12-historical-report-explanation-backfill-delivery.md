# 历史报告 explanation 懒回填实现说明

**日期**: 2026-04-12

**对应设计**:
- [2026-04-12-historical-report-explanation-backfill-design.md](E:/developSoft/ideaworkspace/voice-interview/docs/archive/superpowers/specs/2026-04-12-historical-report-explanation-backfill-design.md)

**对应实施计划**:
- [2026-04-12-historical-report-explanation-backfill-implementation.md](E:/developSoft/ideaworkspace/voice-interview/docs/archive/superpowers/plans/2026-04-12-historical-report-explanation-backfill-implementation.md)

## 1. 本次实现了什么

本次实现完成了历史报告 explanation 的最小闭环：

- JDBC 报告存储层支持读取 `report + reportVersion`
- explanation service 提供纯规则回填入口
- `SimpleInterviewEngine#getReport(...)` 支持历史报告按需懒回填 explanation
- 回填成功后回写 `reportVersion = v2`
- 回填失败、上下文不足、持久化 payload 异常时降级返回旧报告或基于 session 重建，不对外抛 500

## 2. 代码改动范围

### 2.1 Store 层

新增：

- [PersistedInterviewReport.java](E:/developSoft/ideaworkspace/voice-interview/voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/PersistedInterviewReport.java)
- [JdbcInterviewReportStoreTest.java](E:/developSoft/ideaworkspace/voice-interview/voice-interview-backend/src/test/java/com/interview/module/interview/engine/store/JdbcInterviewReportStoreTest.java)

修改：

- [InterviewReportStore.java](E:/developSoft/ideaworkspace/voice-interview/voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/InterviewReportStore.java)
- [NoopInterviewReportStore.java](E:/developSoft/ideaworkspace/voice-interview/voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/NoopInterviewReportStore.java)
- [JdbcInterviewReportStore.java](E:/developSoft/ideaworkspace/voice-interview/voice-interview-backend/src/main/java/com/interview/module/interview/engine/store/JdbcInterviewReportStore.java)

落地结果：

- store 现在可以读取持久化 report 的版本元信息
- 默认最新版本统一为 `v2`
- 空白版本读出时归一化为 `null`，表示未知/旧版本
- 写入时 `null/blank` 版本统一落为 `v2`

### 2.2 Explanation Service

修改：

- [InterviewReportExplanationService.java](E:/developSoft/ideaworkspace/voice-interview/voice-interview-backend/src/main/java/com/interview/module/interview/service/InterviewReportExplanationService.java)
- [InterviewReportExplanationServiceTest.java](E:/developSoft/ideaworkspace/voice-interview/voice-interview-backend/src/test/java/com/interview/module/interview/service/InterviewReportExplanationServiceTest.java)

落地结果：

- 新增 `backfillMissingExplanations(...)`
- 该入口只补缺失 explanation，保留已有 explanation，不触发 LLM
- `enrichReport(...)` 改为先生成 canonical rule explanations，再做 polish
- 已锁定“回填不改 explanation 之外字段”的测试边界

### 2.3 Engine 读路径

修改：

- [SimpleInterviewEngine.java](E:/developSoft/ideaworkspace/voice-interview/voice-interview-backend/src/main/java/com/interview/module/interview/engine/SimpleInterviewEngine.java)
- [SimpleInterviewEngineIntegrationTest.java](E:/developSoft/ideaworkspace/voice-interview/voice-interview-backend/src/test/java/com/interview/module/interview/service/SimpleInterviewEngineIntegrationTest.java)

落地结果：

- `IN_PROGRESS` 会话仍返回实时 report，不走历史回填
- 已持久化 completed report 读取时会先判断 explanation 是否缺失
- explanation 完整则直接返回
- explanation 缺失且上下文完整时，调用规则回填并保存为 `v2`
- 回写失败时返回旧 persisted report
- persisted payload 为 `null` 时，直接基于当前 session snapshot 重建 report

## 3. 当前行为说明

### 3.1 正常新报告

- 新报告生成路径保持不变
- 仍由 `toReportView(...) -> enrichReport(...)` 生成 explanation
- 仍允许走现有 polish 链路

### 3.2 历史旧报告

当调用 `GET /api/interviews/{sessionId}/report` 且命中 persisted report 时：

1. 先读 persisted report + `reportVersion`
2. 若 explanation 已完整，直接返回
3. 若 explanation 缺失：
   - 从当前 session snapshot / rounds 恢复上下文
   - 用规则回填 explanation
   - 保存为 `v2`
   - 返回补完后的 report

### 3.3 降级路径

以下情况不会对外抛 500：

- persisted report explanation 缺失，但 session 上下文不完整
- explanation 回填过程中抛异常
- 回写数据库失败
- persisted report payload 本身为 `null`

降级策略：

- 优先返回原 persisted report
- 如果 persisted payload 已损坏为 `null`，则基于当前 session 重建 report

## 4. 测试结果

已验证通过的核心测试切片：

```bash
mvn "-Dmaven.repo.local=E:/developSoft/ideaworkspace/voice-interview/.worktrees/historical-report-explanation-backfill/voice-interview-backend/.m2/repository" "-Dtest=JdbcInterviewReportStoreTest,InterviewReportExplanationServiceTest,SimpleInterviewEngineIntegrationTest" test
```

结果：

- `57 tests, 0 failures`

重点覆盖：

- persisted report 版本读写
- rule-only explanation 回填
- `enrichReport(...)` 基于 canonical rule explanations 做 polish
- 历史报告首读懒回填
- `v2` 但 explanation 缺失仍回填
- explanation 完整时不重复回填
- 保存失败降级
- session 上下文不完整降级
- persisted payload 为 `null` 时重建 report
- 历史回填不调用 `polishInterviewReportExplanation(...)`

## 5. 变更提交

本次功能相关提交：

- `962169b` `feat(report): add persisted report version metadata`
- `11745f3` `test(report): extend persisted report version coverage`
- `660948e` `feat(report): add rule-only explanation backfill`
- `12b19cc` `fix(report): 改为基于规则解释执行润色`
- `e9bb11f` `feat(report): backfill historical explanations on read`
- `c921579` `fix(report): clarify backfill fallback and preserve payload`

## 6. 当前已知边界

- 当前默认最新版本固定为 `v2`
- 若未来引入 `v3+`，需要同步调整版本策略
- 当前仍假设 `reportJson` 大多数情况下可正常反序列化
- 本次没有引入批处理脚本，也没有做历史报告全量重算
- 本次没有接入 mobile/admin 新展示改动，只补后端读路径与解释回填能力
