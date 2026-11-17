
# =========================
# BUILD STAGE
# =========================
FROM node:22-alpine AS build

WORKDIR /app

# Copy dependency files
COPY package*.json ./

# Install dependencies
RUN npm ci

# Copy frontend source
COPY . .

# Build React application
RUN npm run build


# =========================
# NGINX RUNTIME STAGE
# =========================
FROM nginx:alpine

# Remove default Nginx website
RUN rm -rf /usr/share/nginx/html/*

# Copy custom Nginx SPA routing config
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy production build
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
