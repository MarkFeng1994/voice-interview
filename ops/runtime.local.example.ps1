# Copy this file to `ops/runtime.local.ps1` and fill in your local values.
# Do not commit the real `runtime.local.ps1`.

# Backend
$env:SPRING_PROFILES_ACTIVE = 'dev,openai,dashscope'
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:3306/voice-interview?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:SPRING_DATASOURCE_USERNAME = 'root'
$env:SPRING_DATASOURCE_PASSWORD = 'change-me'

# Providers
$env:APP_AI_PROVIDER = 'langchain4j'
$env:APP_ASR_PROVIDER = 'dashscope'
$env:APP_TTS_PROVIDER = 'dashscope'

# LLM provider
$env:APP_OPENAI_AI_BASE_URL = 'https://your-llm-proxy.example.com/v1'
$env:APP_OPENAI_AI_API_KEY = 'replace-me'
$env:APP_OPENAI_AI_MODEL = 'gpt-5.4-mini'

# DashScope realtime ASR / TTS
$env:APP_DASHSCOPE_BASE_URL = 'wss://dashscope.aliyuncs.com/api-ws/v1/realtime'
$env:APP_DASHSCOPE_API_KEY = 'replace-me'
$env:APP_DASHSCOPE_ASR_MODEL = 'qwen3-asr-flash-realtime'
$env:APP_DASHSCOPE_ASR_LANGUAGE = 'zh'
$env:APP_DASHSCOPE_TTS_MODEL = 'qwen3-tts-flash-realtime'
$env:APP_DASHSCOPE_TTS_VOICE = 'Cherry'
$env:APP_DASHSCOPE_TTS_MODE = 'commit'

# Mobile
$env:VITE_API_BASE_URL = 'http://127.0.0.1:8080'
