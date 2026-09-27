# Angular UI served by nginx, which also proxies /api to the backend. Build context: frontend/
# docker build -t access-orchestrator-frontend frontend
# docker run -p 4200:80 -e BACKEND_URL=http://host.docker.internal:8080 access-orchestrator-frontend

FROM node:18-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration production

FROM nginx:1.27-alpine
# Rendered to /etc/nginx/conf.d/default.conf at start-up, substituting ${BACKEND_URL}
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist/frontend/browser /usr/share/nginx/html
ENV BACKEND_URL=http://backend:8080
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
