# syntax=docker/dockerfile:1
FROM --platform=$BUILDPLATFORM node:22-bookworm-slim AS build
WORKDIR /workspace
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY frontend/ ./
RUN npm run build

FROM nginx:stable-alpine AS runtime
LABEL org.opencontainers.image.licenses="GPL-3.0-only"
RUN apk add --no-cache jq
COPY --from=build /workspace/dist /usr/share/nginx/html
COPY LICENSE /usr/share/doc/ready2race/LICENSE
COPY frontend/docker/default.conf.template /etc/nginx/templates/default.conf.template
COPY frontend/docker/40-runtime-config.sh /docker-entrypoint.d/40-runtime-config.sh
RUN chmod +x /docker-entrypoint.d/40-runtime-config.sh
ENV BACKEND_UPSTREAM=backend:8080
EXPOSE 80
