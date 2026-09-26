FROM node:22-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build
FROM nginx:1.28-alpine
COPY nginx.conf /etc/nginx/templates/nginx.conf.template
ENV BACKEND_URL=http://backend:8011
ENV NGINX_ENVSUBST_OUTPUT_DIR=/tmp/nginx-config
ENV NGINX_ENVSUBST_FILTER=^BACKEND_URL$
COPY --from=build /app/dist /usr/share/nginx/html
USER nginx
EXPOSE 8080
CMD ["nginx", "-c", "/tmp/nginx-config/nginx.conf", "-g", "daemon off;"]
