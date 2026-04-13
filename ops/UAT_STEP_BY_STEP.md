# UAT Step By Step

## 目标

按固定顺序执行一轮上线前验收，尽量减少来回切换和漏测。

## 当前可用地址

- 手机端 H5：`http://127.0.0.1:5173/#/`
- 管理端：`http://127.0.0.1:5174/`
- 后端：`http://127.0.0.1:8080`

如果需要真机测试：

- 把 `127.0.0.1` 换成电脑的局域网 IP
- 保持端口不变：`5173`、`5174`、`8080`
- 确保电脑防火墙未拦截这些端口

## 测试账号

- 手机端 / 管理端：`admin / 123456`

## 参考文档

- [MOBILE_UAT_MATRIX.md](E:/developSoft/ideaworkspace/voice-interview/ops/MOBILE_UAT_MATRIX.md)
- [PRE_RELEASE_SIGNOFF.md](E:/developSoft/ideaworkspace/voice-interview/ops/PRE_RELEASE_SIGNOFF.md)
- [TROUBLESHOOTING.md](E:/developSoft/ideaworkspace/voice-interview/ops/TROUBLESHOOTING.md)

## Step 1. 环境确认

先在浏览器打开：

1. `http://127.0.0.1:8080/actuator/health`

预期结果：

- `health` 返回 `UP`

如果这一步失败，不要继续后面的业务验收。

## Step 2. 手机端登录与首页

打开：

- `http://127.0.0.1:5173/#/`

执行：

1. 使用测试账号登录
2. 观察首页“服务状态”卡片

预期结果：

- 登录成功并进入首页
- 首页显示当前账号昵称
- “服务状态”显示 `openai / dashscope / dashscope`

如果需要直接验证 provider 运行链路，请在登录后带 token 请求：

```powershell
$login = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8080/api/auth/login" -ContentType "application/json" -Body '{"username":"admin","password":"123456"}'
$token = $login.data.token
Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:8080/api/system/providers" -Headers @{ Authorization = "Bearer $token" }
```

预期结果：

- provider 运行链路显示：
  - `AI = openai`
  - `ASR = dashscope`
  - `TTS = dashscope`

## Step 3. 配置页与开始面试

执行：

1. 点击“立即开始”
2. 进入配置页
3. 先选择 `后端核心能力`
4. 点击“进入面试”
5. 返回配置页后，再选择 `微服务排障`
6. 再次点击“进入面试”

预期结果：

- 配置页能正常加载预设列表
- 不同预设会进入不同题组
- `微服务排障` 首题应接近“故障定位”

## Step 4. 会话页主流程

在会话页执行：

1. 点击“开始面试”
2. 播放题目音频
3. 输入一段文字回答
4. 点击“发送回答”
5. 观察会话是否自动推进到下一轮提问
6. 点击“跳过”
7. 点击“结束”
8. 在确认框里点击“确认结束”

预期结果：

- 状态从预览变为进行中
- 首题正常出现
- WebSocket 连接后可看到“实时通道已连接”或实时更新提示
- 发送回答后无需手动刷新，直接进入下一轮提问
- 下一轮提问可能是同一题的追问，也可能是下一题
- 跳过和结束动作都能正常生效
- 点击结束后会先出现确认框
- 确认结束后应自动跳转到带 `sessionId` 的报告页

## Step 5. 语音链路

这一步优先在真机上做。

在会话页执行：

1. 录一段音，或选择一段音频
2. 点击“重新识别”
3. 点击“发送回答”
4. 播放 AI 音频

预期结果：

- 音频上传成功
- ASR 转写成功
- 会话状态推进成功
- AI 音频可播放

## Step 6. 历史、报告、个人中心

执行：

1. 打开历史页
2. 点击“继续会话”
3. 回到历史页后点击“查看报告”
4. 打开个人中心
5. 修改昵称并保存
6. 退出登录

预期结果：

- 历史页能看到最近会话
- 历史页支持按 `全部 / 进行中 / 已完成 / 已结束` 筛选
- 继续会话可以恢复到正确的 session，并自动尝试重连实时链路
- 报告页显示总分、优势、待加强、建议、题目明细
- 已结束会话的报告会显示“中途复盘”说明
- 昵称修改后刷新仍生效
- 退出登录后回到登录页

## Step 7. 管理端后台

打开：

- `http://127.0.0.1:5174/`

执行：

1. 使用测试账号登录
2. 创建一个分类，例如：`UAT-缓存`
3. 创建一道题
4. 编辑这道题
5. 使用“文本导入”导入两道题
6. 查看导入任务状态
7. 查看“面试记录与报告”
8. 查看“Provider 调用指标”

建议导入文本：

```text
缓存击穿
请说明缓存击穿、缓存雪崩和缓存穿透的区别，并给出治理策略。

---

数据库索引
请讲一下联合索引最左前缀原则，以及你在线上调优时怎么验证索引是否命中。
```

预期结果：

- 分类 CRUD 正常
- 题目 CRUD 正常
- 导入任务状态为 `SUCCESS`
- 报表区能看到最近会话
- Provider 指标区能看到：
  - `AI · openai`
  - `ASR · dashscope`
  - `TTS · dashscope`

## Step 8. 结果记录方式

建议按下面格式记录：

- `A-环境`: 通过 / 失败
- `B-登录首页`: 通过 / 失败
- `C-配置页`: 通过 / 失败
- `D-会话文本链路`: 通过 / 失败
- `E-会话语音链路`: 通过 / 失败
- `F-历史报告`: 通过 / 失败
- `G-个人中心`: 通过 / 失败
- `H-后台分类题目`: 通过 / 失败
- `I-文本导入`: 通过 / 失败
- `J-后台报表指标`: 通过 / 失败

## Step 9. 失败时必须记录的信息

每个失败项至少记录：

1. 失败页面
2. 你点了什么
3. 页面报错文字
4. 发生时间
5. 响应头里的 `X-Request-Id`，如果能拿到就一起记录

记录完后再去看：

- [TROUBLESHOOTING.md](E:/developSoft/ideaworkspace/voice-interview/ops/TROUBLESHOOTING.md)

## 收尾

全部执行完成后：

1. 回到 [PRE_RELEASE_SIGNOFF.md](E:/developSoft/ideaworkspace/voice-interview/ops/PRE_RELEASE_SIGNOFF.md)
2. 把通过项逐条勾掉
3. 把失败项发回主线程继续修
