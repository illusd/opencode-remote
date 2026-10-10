# Dockerfile for Fly.io / Railway / Render
# 多階段建置，最終映像約 60MB

# ===== Builder 階段 =====
FROM node:22-alpine AS builder

WORKDIR /app

# 安裝建置依賴
RUN apk add --no-cache python3 make g++

# 複製 package.json 並安裝依賴 (利用快取)
COPY package*.json ./
RUN npm ci --only=production

# 複製源碼並建置
COPY . .
RUN npm run build

# ===== Runner 階段 =====
FROM node:22-alpine AS runner

WORKDIR /app

# 建立非 root 使用者
RUN addgroup -g 1001 -S nodejs && \
    adduser -S nodejs -u 1001

# 從 builder 複製建置產物
COPY --from=builder --chown=nodejs:nodejs /app/node_modules ./node_modules
COPY --from=builder --chown=nodejs:nodejs /app/dist ./dist
COPY --from=builder --chown=nodejs:nodejs /app/package.json ./

USER nodejs

EXPOSE 8080

ENV NODE_ENV=production
ENV PORT=8080

CMD ["node", "dist/standalone-server.js"]