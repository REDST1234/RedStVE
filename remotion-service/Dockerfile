FROM node:18-bullseye

WORKDIR /app

# Install dependencies required by Puppeteer/Chromium for Remotion
RUN apt-get update && apt-get install -y \
    ffmpeg \
    libnss3 \
    libdbus-1-3 \
    libatk1.0-0 \
    libasound2 \
    libxss1 \
    libgtk-3-0 \
    libgbm1 \
    && rm -rf /var/lib/apt/lists/*

COPY package*.json ./
RUN npm install

COPY . .

# Build remotion bundles
RUN npm run build

EXPOSE 3001

CMD ["npm", "run", "server"]
