# syntax=docker/dockerfile:1.7
#
# mms-ui production image. Multi-stage:
#   1. node:   npm ci + vite build
#   2. nginx:  serves the built bundle + reverse-proxies /api to mms-service
#
# Build context must be this module's directory:
#   docker build -t mms/mms-ui:latest mms-ui/
# docker-compose.yml handles this automatically via build.context: mms-ui.

# ─── Stage 1: build ───────────────────────────────────────────────────────────
FROM node:25-alpine AS builder

WORKDIR /app

# Install manifests first so npm layer caches across code-only changes.
COPY package.json package-lock.json* ./
RUN npm ci

COPY . .
RUN npm run build


# ─── Stage 2: runtime ─────────────────────────────────────────────────────────
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget -q --spider http://127.0.0.1/ || exit 1
