.PHONY: help build up down test logs clean dev prod migrate

help: ## Show this help message
	@echo "Available commands:"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'

build: ## Build all Docker images
	docker-compose build

up: ## Start services in detached mode
	docker-compose up -d

dev: ## Start services with MailHog for development
	docker-compose --profile dev up --build

down: ## Stop all services
	docker-compose down

dev-down: ## Stop all services including dev profile
	docker-compose --profile dev down

test: ## Run tests locally with SQLite
	python -m pytest tests/ -v

test-docker: ## Run tests with Docker (PostgreSQL + MailHog)
	docker-compose -f docker-compose.yml -f docker-compose.test.yml up --build --abort-on-container-exit
	
logs: ## Show logs from all services
	docker-compose logs -f

logs-web: ## Show web service logs
	docker-compose logs -f web

logs-worker: ## Show worker service logs
	docker-compose logs -f worker

clean: ## Remove containers, volumes, and images
	docker-compose down -v --rmi local

migrate: ## Initialize database (run once)
	docker-compose exec web python -c "from shared.db import init_db, make_engine; import os; init_db(make_engine(os.environ['DATABASE_URL']))"

shell-web: ## Open shell in web container
	docker-compose exec web sh

shell-worker: ## Open shell in worker container
	docker-compose exec worker sh

shell-db: ## Open PostgreSQL shell
	docker-compose exec db psql -U newsletter -d newsletter

backup: ## Backup database
	docker-compose exec db pg_dump -U newsletter newsletter > backup_$$(date +%Y%m%d_%H%M%S).sql
