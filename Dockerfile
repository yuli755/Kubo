# 构建阶段
FROM node:20-alpine AS build

# 设置工作目录
WORKDIR /app

# 启用 corepack：pnpm 版本由 package.json 的 "packageManager": "pnpm@9.0.0" 锁定，
# 与 CI（pnpm/action-setup@v4）保持一致。
RUN corepack enable

# 依赖清单：pnpm-lock.yaml 必须与 package.json 一起复制，frozen-lockfile 才能生效。
# 历史这里用 yarn（且仓库无 yarn.lock），导致：① 依赖解析与 CI 不一致；
# ② pnpm.overrides（axios 1.20.0 漏洞锁定）对 yarn 不生效 —— 镜像可能打出带漏洞的 axios。
COPY package.json pnpm-lock.yaml ./

# 安装依赖（国内构建环境可取消下一行注释换 npmmirror 源）
# RUN pnpm config set registry https://registry.npmmirror.com
RUN pnpm install --frozen-lockfile

# 复制源代码
COPY . .

# 构建生产版本（等价于 vite build --mode production）
RUN pnpm build

# 生产阶段
FROM nginx:alpine

# 复制构建产物到nginx目录
COPY --from=build /app/dist /usr/share/nginx/html

# 复制nginx配置
COPY nginx.conf /etc/nginx/nginx.conf

# 暴露端口
EXPOSE 80

# 启动nginx
CMD ["nginx", "-g", "daemon off;"]