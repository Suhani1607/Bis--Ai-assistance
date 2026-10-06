# BIS AI Assistant 

An AI-powered conversational assistant for the Bureau of Indian Standards (BIS) built with RAG, clause-level citations, and multilingual support.

## Project Structure

```
bis-ai-assistant/
├── frontend/          # Next.js 14 + Tailwind + shadcn/ui
├── backend/           # Spring Boot 3 + PostgreSQL
├── rag/               # Python — LangChain + Qdrant RAG pipeline
├── docker/            # Docker Compose + Dockerfiles
└── docs/              # Architecture docs
```

## Tech Stack

| Layer       | Technology                              |
|-------------|----------------------------------------|
| Frontend    | Next.js 14, Tailwind CSS, shadcn/ui     |
| Backend     | Spring Boot 3, Java 21, PostgreSQL 16   |
| AI/RAG      | Python 3.11, LangChain, Qdrant          |
| LLM         | OpenAI-compatible (GPT-4o / local)      |
| Auth        | Spring Security + JWT                   |
| Deployment  | Docker Compose                          |

## Features

- 🔍 Hybrid semantic + keyword search over BIS documents
- 📄 Clause-level citations with IS number and section references
- 🌐 Multilingual: English + Hindi
- 💬 Conversation memory across multi-turn sessions
- 📥 Automated PDF ingestion pipeline
- 🎙️ Voice-ready architecture (WebSpeech API)
- 🔐 JWT-based authentication

## Quick Start

```bash
cp .env.example .env          # fill in your API keys
docker compose up --build     # starts all services
```

Then open http://localhost:3000

## Build Phases

1. ✅ Architecture
2. ✅ Database Schema
3. ⬜ Backend APIs
4. ⬜ RAG Pipeline
5. ⬜ Frontend
6. ⬜ Authentication
7. ⬜ Testing
8. ⬜ Docker Deployment
