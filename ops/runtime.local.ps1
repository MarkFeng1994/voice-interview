# Local runtime config — do not commit

# Spring profiles
$env:SPRING_PROFILES_ACTIVE = 'dev,openai,dashscope'

# MySQL
$env:SPRING_DATASOURCE_URL      = 'jdbc:mysql://47.116.36.30:3306/voice-interview?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:SPRING_DATASOURCE_USERNAME = 'root'
$env:SPRING_DATASOURCE_PASSWORD = 'DFfsh83374861+'

# Providers
$env:APP_AI_PROVIDER  = 'openai'
$env:APP_ASR_PROVIDER = 'dashscope'
$env:APP_TTS_PROVIDER = 'dashscope'

# LLM (OpenAI-compatible)
$env:APP_OPENAI_AI_BASE_URL = 'https://api.dejong21.me/v1'
$env:APP_OPENAI_AI_API_KEY  = 'sk-6vFNWBaPo4Nnzxh03wqDVc1uswwIs6n7HyBzqDQjK9O7sJwZ'
$env:APP_OPENAI_AI_MODEL    = 'gpt-5.4'

# DashScope realtime ASR / TTS
$env:APP_DASHSCOPE_BASE_URL      = 'wss://dashscope.aliyuncs.com/api-ws/v1/realtime'
$env:APP_DASHSCOPE_API_KEY       = 'sk-58aa37b7b4694dc79b8d097826ad73e1'
$env:APP_DASHSCOPE_ASR_MODEL     = 'qwen3-asr-flash-realtime'
$env:APP_DASHSCOPE_ASR_LANGUAGE  = 'zh'
$env:APP_DASHSCOPE_TTS_MODEL     = 'qwen3-tts-flash-realtime'
$env:APP_DASHSCOPE_TTS_VOICE     = 'Cherry'
$env:APP_DASHSCOPE_TTS_MODE      = 'commit'

# Mobile
$env:VITE_API_BASE_URL = 'http://127.0.0.1:8080'
