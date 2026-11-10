FROM node:20-alpine AS build
WORKDIR /build
COPY package*.json ./
# 不锁死 npm ci：仓库里可能没有 package-lock.json
RUN npm install --no-audit --no-fund --registry=https://registry.npmmirror.com
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist /usr/share/nginx/html
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
