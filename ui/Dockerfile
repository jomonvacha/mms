# Multi-stage Dockerfile: build with Node, serve with Nginx on port 3000

## Build stage
FROM node:18-alpine AS build

WORKDIR /app

# Pass-through build-time env for Vite
ARG VITE_API_BASE_URL
ARG VITE_PROXY_TARGET
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
ENV VITE_PROXY_TARGET=${VITE_PROXY_TARGET}

COPY package.json package-lock.json* ./
RUN npm install

COPY . .

RUN npm run build

## Runtime stage
FROM nginx:alpine

COPY nginx.conf /etc/nginx/nginx.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 3000

CMD ["nginx", "-g", "daemon off;"]

