# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AI Video Generation Platform — full-stack monorepo with Java backend and TypeScript frontend.

## Commands

### Backend (Java 21, Maven)
```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev   # Start dev server on :8080
mvn compile
mvn test
mvn test -Dtest=AiVideoApplicationTests
```

### Frontend (Vite + React + Tailwind)
```bash
cd frontend
npm run dev      # Start dev server on :5173, proxies /api -> localhost:8080
npm run build    # tsc -b && vite build
npm run preview  # Preview production build
```

## Architecture

### Backend — `backend/`

Standard Spring Boot 3.2.5 layered architecture:

- **Config layer** (`com.bytedance.aivideo.config`): MyBatis Plus (pagination plugin), Redis (JSON serializer via GenericJackson2JsonRedisSerializer), RabbitMQ (Jackson2JsonMessageConverter)
- **Controller layer** (`com.bytedance.aivideo.controller`): REST endpoints under `/api/`
- **Profiles**: `dev` (localhost MySQL/Redis/RabbitMQ, SQL debug logging) and `prod` (credentials via env vars, info-level logging, rolling file appenders)
- **Logging**: Logback with `logback-spring.xml` — dev uses console only; prod adds rolling file (30-day) + error-only file (60-day)

Key dependencies: Spring Boot Web, MyBatis Plus 3.5.7, MySQL Connector, Spring Data Redis (Lettuce), Spring AMQP (RabbitMQ), Lombok.

### Frontend — `frontend/`

Vite 6 + React 18 + TypeScript + Tailwind CSS 3 (PostCSS).

- Standard Vite React project with `src/` entry point
- Tailwind utility classes via `@tailwind` directives in `index.css`
- Vite dev server proxies `/api/*` requests to `http://localhost:8080`
- No routing library configured yet — bare React setup
