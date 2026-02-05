FROM node:22-bookworm-slim

WORKDIR /app

# build deps for native modules (better-sqlite3)
RUN apt-get update && apt-get install -y --no-install-recommends \
  python3 make g++ \
  && rm -rf /var/lib/apt/lists/*

COPY package*.json ./
RUN npm ci

COPY . .

ENV PORT=3001
ENV VITE_PORT=5173
EXPOSE 3001 5173

CMD ["npm","run","dev","--","--host","0.0.0.0"]
