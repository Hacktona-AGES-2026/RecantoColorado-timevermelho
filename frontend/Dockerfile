# Frontend do FilaVIVA (Next.js 16). O navegador chama /api/... no próprio Next, que repassa para o backend
# (next.config.ts, BACKEND_URL). Construído a partir da pasta frontend/:  docker build -t filaviva-frontend frontend

FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# endereço do backend dentro da rede do Docker (o mesmo vale na hora de rodar)
ARG BACKEND_URL=http://backend:8080
ENV BACKEND_URL=$BACKEND_URL \
    NEXT_TELEMETRY_DISABLED=1
RUN npm run build && npm prune --omit=dev --no-audit --no-fund

FROM node:24-alpine
WORKDIR /app
ARG BACKEND_URL=http://backend:8080
ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    BACKEND_URL=$BACKEND_URL \
    PORT=3000
COPY --from=build --chown=node:node /app/package.json /app/next.config.ts ./
COPY --from=build --chown=node:node /app/node_modules node_modules
COPY --from=build --chown=node:node /app/.next .next
COPY --from=build --chown=node:node /app/public public
USER node
EXPOSE 3000
CMD ["node_modules/.bin/next", "start", "-p", "3000"]
