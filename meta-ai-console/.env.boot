# 本地 java -jar 直连验证使用根路径页面
VITE_APP_BASE_PATH=/

# 本地 java -jar 没有 Vite proxy / Nginx rewrite，API 直接请求 Spring Boot /v1/**
VITE_API_BASE_URL=/

# 本地直连轻量 API Token，必须与后端 metax.ai.security.api-token 保持一致
# VITE_ 变量会进入浏览器构建产物，不能作为生产安全凭证
VITE_METAX_API_TOKEN=sk-metax-123456
