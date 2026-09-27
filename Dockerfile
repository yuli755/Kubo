# =============================================================================
# 阶段 1：静态编译构建
# =============================================================================
FROM node:20-alpine AS build

WORKDIR /app

# 启用 corepack：由 package.json 的 "packageManager": "pnpm@9.0.0" 锁定
RUN corepack enable

# 仅复制依赖元数据，利用分层缓存
COPY package.json pnpm-lock.yaml ./

# 冻结模式安装依赖（依赖锁定防漂移）
RUN pnpm install --frozen-lockfile

# 复制前端源代码
COPY . .

# 生产环境打包构建
RUN pnpm build

# =============================================================================
# 阶段 2：轻量生产静态交付（Nginx Alpine 基础镜像仅 ~25MB）
# =============================================================================
FROM nginx:alpine

# 清理默认静态网页
RUN rm -rf /usr/share/nginx/html/*

# 从构建阶段复制打包产物
COPY --from=build /app/dist /usr/share/nginx/html

# 覆盖 Nginx 生产反向代理配置
COPY nginx.conf /etc/nginx/nginx.conf

# 暴露 HTTP 服务端口
EXPOSE 80

# 容器探针健康检查
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -q --spider http://127.0.0.1/ || exit 1

# 启动 Nginx
CMD ["nginx", "-g", "daemon off;"]
