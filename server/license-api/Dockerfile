FROM node:22-alpine

ENV NODE_ENV=production
ENV PORT=8787
WORKDIR /app

COPY package.json ./
RUN npm install --omit=dev --ignore-scripts --no-audit --no-fund \
    && npm cache clean --force

COPY src ./src
COPY admin ./admin

USER node
EXPOSE 8787
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
  CMD node -e "fetch('http://127.0.0.1:8787/ready').then(r=>process.exit(r.ok?0:1)).catch(()=>process.exit(1))"
CMD ["node", "src/server.mjs"]
