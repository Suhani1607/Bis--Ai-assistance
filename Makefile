.PHONY: help dev build test clean logs ingest

help:
	@echo "BIS AI Assistant — Development Commands"
	@echo ""
	@echo "  make dev          Start all services in dev mode (docker compose)"
	@echo "  make build        Build all Docker images"
	@echo "  make test         Run backend + RAG tests"
	@echo "  make ingest       Trigger incremental document ingestion"
	@echo "  make evaluate     Run Ragas evaluation suite"
	@echo "  make logs         Tail all service logs"
	@echo "  make clean        Stop and remove containers + volumes"

dev:
	cp -n .env.example .env 2>/dev/null || true
	docker compose up --build

build:
	docker compose build --parallel

test:
	@echo "── Backend tests ──"
	cd backend && mvn test -pl . --no-transfer-progress
	@echo "── RAG tests ──"
	cd rag && python -m pytest tests/ -v

evaluate:
	cd rag && python evaluation/evaluate.py

logs:
	docker compose logs -f --tail=100

ingest:
	curl -X POST http://localhost:8080/api/v1/admin/ingestion/crawl \
	  -H "Authorization: Bearer $$(cat .token)" \
	  -H "Content-Type: application/json" \
	  -d '{"jobType":"INCREMENTAL"}'

shell-backend:
	docker compose exec backend sh

shell-rag:
	docker compose exec rag bash

clean:
	docker compose down -v --remove-orphans
	docker system prune -f
