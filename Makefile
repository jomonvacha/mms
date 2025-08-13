.PHONY: help db-up db-down db-destroy db-logs up down logs app-up build test verify clean

help:
	@echo "Common targets:"
	@echo "  db-up       - Start PostgreSQL container"
	@echo "  db-down     - Stop PostgreSQL container"
	@echo "  db-destroy  - Stop and remove DB volume (DATA LOSS)"
	@echo "  db-logs     - Tail PostgreSQL logs"
	@echo "  up          - Start app + postgres via docker-compose"
	@echo "  app-up      - Start only the app (depends on healthy DB)"
	@echo "  down        - Stop all compose services"
	@echo "  logs        - Tail app logs"
	@echo "  build       - Maven clean package"
	@echo "  test        - Maven test"
	@echo "  verify      - Maven verify (with JaCoCo)"
	@echo "  clean       - Maven clean"

db-up:
	docker-compose up -d postgres

db-down:
	docker-compose stop postgres

db-destroy:
	docker-compose down -v

db-logs:
	docker-compose logs -f postgres

up:
	docker-compose up -d

app-up:
	docker-compose up -d app

down:
	docker-compose down

logs:
	docker-compose logs -f app

build:
	mvn clean package

test:
	mvn test

verify:
	mvn verify

clean:
	mvn clean

