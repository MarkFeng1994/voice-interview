# 实施计划：简历驱动面试出题 (resume-driven-interview)

## 任务类型
- [x] 后端 (-> Codex)
- [x] 前端 (-> Gemini)
- [x] 全栈 (-> 并行)

## 技术方案

用户上传 PDF 简历 → 后端 PDFBox 解析提取文本 → AI 提取技术关键词 → 匹配用户题库分类/题目 → AI 补充生成缺失方向的题目 → 组装个性化题序列 → 传入现有 InterviewEngine.startSession()。无简历时退回原有预设出题模式，完全向后兼容。

### 核心设计原则
- **InterviewEngine 不改** — 只替换上游的题目来源，引擎接口 `startSession(List<InterviewQuestionCard>, ...)` 保持不变
- **三级降级** — 题库匹配优先 → AI 补题次之 → 预设兜底
- **前端双模式** — setup.vue 增加 Tab 切换（预设 / 简历），简历模式走状态机 IDLE→UPLOADING→PARSING→PREVIEW

---

## 实施步骤

### 后端 Step 1：新增简历上传接口

**修改文件**：
- `MediaController.java` — 新增 `POST /api/media/upload/resume`
- `LocalMediaStorageService.java` — 新增 `storeResumePdf(MultipartFile)` + `loadOwned(fileId, userId)`
- `MediaFileLifecycleService.java` — 新增 `recordResumeFile()`
- `MediaFileRecordRepository.java` — 新增 `findByIdAndUserId()`

**要点**：
- 只接受 `application/pdf`，`biz_type = 'RESUME_PDF'`
- 独立过期策略 `app.media.resume-expire-hours`
- 读取时校验 owner

**伪代码**：
```java
@PostMapping("/upload/resume")
public ApiResponse<ResumeUploadResult> uploadResume(
    @RequestParam MultipartFile file, HttpServletRequest request) {
    UserProfile profile = currentUserResolver.requireProfile(request);
    validatePdf(file);
    StoredMediaFile stored = mediaStorageService.storeResumePdf(file);
    mediaLifecycleService.recordResumeFile(stored, profile.id());
    return ApiResponse.success(new ResumeUploadResult(stored.fileId()));
}
```

### 后端 Step 2：PDF 解析

**新增文件**：
- `pom.xml` — 添加 `org.apache.pdfbox:pdfbox` 依赖
- `module/interview/resume/ResumeTextExtractor.java` — 接口
- `module/interview/resume/PdfBoxResumeTextExtractor.java` — 实现

**要点**：
- 限制 `max-pages=8`、`max-text-chars=12000`
- 文本为空时返回 `RESUME_TEXT_EMPTY` 错误
- 清洗空白、去重复页眉页脚

### 后端 Step 3：扩展 AiService

**修改文件**：
- `AiService.java` — 新增两个方法
- `OpenAiCompatibleAiService.java` — 实现
- `MockAiService.java` — Mock 实现

**新增方法**：
```java
ResumeKeywordExtractionResult extractResumeKeywords(String resumeText);
List<GeneratedResumeQuestion> generateResumeQuestions(ResumeQuestionGenerationCommand command);
```

**新增 DTO**：
- `ResumeKeywordExtractionResult(keywords, summary, experienceHighlights)`
- `ResumeQuestionGenerationCommand(resumeSummary, keywords, existingTitles, missingTopics, desiredCount)`
- `GeneratedResumeQuestion(title, prompt, targetKeyword, difficulty)`

**Prompt 设计**：
- 关键词提取：输入简历文本 → 输出 JSON `{summary, keywords[], experienceHighlights[]}`
  - 规则：只提取明确出现的技术/框架/工具，标准化别名(K8s→Kubernetes)，上限 8-12 个
- 补题生成：输入简历摘要+关键词+已有题目+缺失方向 → 输出 JSON `{questions[{title,prompt,targetKeyword,difficulty}]}`
  - 规则：中文面试官语气，每题一个具体问题，不重复已有题库措辞

### 后端 Step 4：题库匹配器

**新增文件**：
- `module/interview/resume/ResumeQuestionMatcher.java`

**修改文件**：
- `LibraryQuestionRepository.java` — 新增 `findAllByUserIdAndCategoryIds()` + `incrementUsedCount()`

**匹配策略**：
1. 标准化关键词（别名映射）
2. 按关键词命中度对 category 评分（精确>别名>子串）
3. 按 category 命中 + 关键词在 title/content 中重叠度对 question 评分
4. 难度多样性加分，高 used_count 降权
5. 去重（normalized title+content）

### 后端 Step 5：编排服务

**新增文件**：
- `module/interview/resume/ResumeInterviewPlannerService.java`
- `module/interview/resume/ResumeInterviewPlan.java`

**核心流程**：
```
1. loadOwned(resumeFileId, userId)       // 校验 owner
2. extractText(pdf)                       // PDFBox 提取文本
3. extractResumeKeywords(text)            // AI 提取关键词
4. match(keywords, categories, questions) // 题库匹配
5. if 匹配不足 → generateResumeQuestions() // AI 补题
6. if 仍不足 → selectPresetFallback()    // 预设兜底
7. return ResumeInterviewPlan(finalQuestions, keywords, ...)
```

### 后端 Step 6：集成到面试启动链路

**修改文件**：
- `InterviewController.StartInterviewRequest` — 新增 `resumeFileId`, `questionCount`
- `InterviewController.startSession()` — 路由到新逻辑
- `InterviewPracticeService` — 新增重载，简历模式调用 PlannerService

**新增接口**：
- `POST /api/interviews/resume-preview` — 预览匹配结果（不创建会话）
  - Request: `{resumeFileId, presetKey?, questionCount?}`
  - Response: `{keywords, matchedCategories, matchedQuestions, generatedQuestions, plan}`

**向后兼容**：`resumeFileId` 为空时走原有预设逻辑，完全不受影响

### 后端 Step 7：持久化题目来源

**修改文件**：
- `InterviewQuestionCard` — 新增 `sourceType`, `sourceQuestionId`, `sourceCategoryId`, `difficulty`
- `JdbcInterviewSessionStore` — 写入真实 question_id, category_id 等

**不需要新表**：现有 `t_interview_question` 和 `t_interview_session.config_json` 已够用

---

### 前端 Step 1：新增 API 和类型

**新增文件**：
- `services/resumeApi.ts` — `uploadResume(filePath)`, `getResumePreview(fileId)`
- `types/interview.ts` — 新增 `ResumePreviewResult`, `ResumeKeyword`, `PreviewQuestion`

### 前端 Step 2：setup.vue 双模式 Tab

**修改文件**：`pages/interview/setup.vue`

**改动**：
- 顶部新增 Tab 切换（系统预设 / 简历出题）
- 预设模式区用 `v-show="setupMode === 'PRESET'"` 包裹
- 简历模式区用 `v-show="setupMode === 'RESUME'"` 包裹
- 简历模式状态机：`IDLE → UPLOADING → PARSING → PREVIEW`
- `goToSession()` 根据 mode 传不同参数

### 前端 Step 3：新增 ResumeUploader 组件

**新增文件**：`components/interview/ResumeUploader.vue`

**要点**：
- 跨端兼容文件选择（条件编译）
  - `#ifdef MP-WEIXIN` → `uni.chooseMessageFile()`
  - `#ifdef H5` → `uni.chooseFile()`
- 上传进度显示
- 支持 10MB 以内 PDF

### 前端 Step 4：新增 ResumePreview 组件

**新增文件**：`components/interview/ResumePreview.vue`

**功能**：
- 显示 AI 提取的技术关键词 Tags（可点击移除）
- 显示匹配到的题目预览列表
- 显示 AI 补充生成的题目
- 底部显示总题数和"开始面试"按钮

### 前端 Step 5：session.vue 兼容

**修改文件**：`pages/interview/session.vue`, `composables/useInterviewSession.ts`

**改动**：
- 路由参数新增 `mode=resume` 和 `resumeFileId`
- `useInterviewSession` 根据 mode 决定创建会话时是传 presetKey 还是 resumeFileId

---

## 关键文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `pom.xml` | 修改 | 添加 pdfbox 依赖 |
| `module/interview/resume/ResumeTextExtractor.java` | 新增 | PDF 解析接口 |
| `module/interview/resume/PdfBoxResumeTextExtractor.java` | 新增 | PDFBox 实现 |
| `module/interview/resume/ResumeQuestionMatcher.java` | 新增 | 题库匹配器 |
| `module/interview/resume/ResumeInterviewPlannerService.java` | 新增 | 编排服务 |
| `module/interview/resume/ResumeInterviewPlan.java` | 新增 | 计划 DTO |
| `AiService.java` | 修改 | 新增 2 个方法 |
| `OpenAiCompatibleAiService.java` | 修改 | 实现新方法 |
| `MockAiService.java` | 修改 | Mock 实现 |
| `MediaController.java` | 修改 | 新增简历上传端点 |
| `LocalMediaStorageService.java` | 修改 | 新增 PDF 存储 + owner 校验 |
| `InterviewController.java` | 修改 | 扩展 StartInterviewRequest + 新增 resume-preview |
| `InterviewPracticeService.java` | 修改 | 新增简历模式启动重载 |
| `InterviewQuestionCard.java` | 修改 | 新增来源元数据 |
| `JdbcInterviewSessionStore.java` | 修改 | 持久化真实题目来源 |
| `LibraryQuestionRepository.java` | 修改 | 新增按分类批量查询 |
| `services/resumeApi.ts` | 新增 | 简历上传/预览 API |
| `types/interview.ts` | 修改 | 新增简历相关类型 |
| `pages/interview/setup.vue` | 修改 | 双模式 Tab + 简历状态机 |
| `components/interview/ResumeUploader.vue` | 新增 | 文件选择+上传组件 |
| `components/interview/ResumePreview.vue` | 新增 | 关键词+题目预览组件 |
| `composables/useInterviewSession.ts` | 修改 | 支持 resume 模式创建会话 |

## 风险与缓解

| 风险 | 缓解措施 |
|------|----------|
| PDF 是纯图片扫描，提取不到文本 | 返回 422 `RESUME_TEXT_EMPTY`，前端引导用户使用文字版简历 |
| AI 关键词提取失败 | 本地别名表兜底，从简历文本做正则匹配常见技术栈 |
| 用户题库为空，匹配不到任何题 | AI 全量生成 + 预设补位，确保不出空序列 |
| 简历解析+AI调用延迟高（5-15秒） | 前端骨架屏+进度提示，resume-preview 可预先调用 |
| 跨端文件选择兼容性 | 条件编译 `#ifdef` 隔离微信/H5/App 路径 |
| 用户 A 使用用户 B 的简历 fileId | loadOwned 强制校验 userId |

## 数据库变更

无需新表。现有字段已覆盖：
- `t_media_file.biz_type` → `RESUME_PDF`
- `t_interview_session.config_json` → 存储 resumeFileId、keywords、summary
- `t_interview_question.source_snapshot` → `LIBRARY` / `AI_GENERATED` / `PRESET`

## SESSION_ID（供 /ccg:execute 使用）
- CODEX_SESSION: 019d12ef-57f3-7bc1-bb0e-0832955db2c5
- GEMINI_SESSION: (Gemini CLI 未返回 session_id)
