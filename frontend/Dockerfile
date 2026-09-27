# Angular UI served by nginx. Build context: frontend/
FROM node:18-alpine AS build
WORKDIR /src

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npx ng build --configuration production

FROM nginx:1.27-alpine

# Template rendered by Nginx entrypoint to /etc/nginx/conf.d/default.conf substituting ${BACKEND_URL}
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /src/dist/frontend/browser /usr/share/nginx/html

ENV BACKEND_URL=http://backend:8080
EXPOSE 8080

CMD ["nginx", "-g", "daemon off;"]
