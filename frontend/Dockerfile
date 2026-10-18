# ============================================================================
# Frontend Angular - multi-stage (build Node -> Nginx).
# Requiere el proyecto CLI `canchadirecta-frontend` dentro de frontend/.
# ============================================================================
FROM node:24-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build -- --configuration production

FROM nginx:1.27-alpine
# Angular 22 emite el build en dist/<proyecto>/browser
COPY --from=build /app/dist/canchadirecta-frontend/browser/ /usr/share/nginx/html/
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
