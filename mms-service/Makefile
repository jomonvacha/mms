.PHONY: help db-up db-down db-destroy db-logs up down logs app-up build test verify clean \
        migrate-dry-run migrate migrate-verify

help:
	@echo "Common targets:"
	@echo "  db-up          - Start PostgreSQL container"
	@echo "  db-down        - Stop PostgreSQL container"
	@echo "  db-destroy     - Stop and remove DB volume (DATA LOSS)"
	@echo "  db-logs        - Tail PostgreSQL logs"
	@echo "  up             - Start the standalone MMS stack"
	@echo "  app-up         - Start only the API and its database"
	@echo "  down           - Stop all compose services"
	@echo "  logs           - Tail app logs"
	@echo "  build          - Maven clean package"
	@echo "  test           - Maven test"
	@echo "  verify         - Maven verify (with JaCoCo)"
	@echo "  clean          - Maven clean"
	@echo ""
	@echo "Mongo→Postgres data migration (requires MMS_MONGODB_URI + MMS_DATABASE_URL):"
	@echo "  migrate-dry-run - Read Mongo, validate, report counts. No writes."
	@echo "  migrate         - Actually copy data into Postgres (idempotent)."
	@echo "  migrate-verify  - Compare source/target counts and sample rows."

db-up:
	docker compose -f ../docker-compose.yml up -d postgres

db-down:
	docker compose -f ../docker-compose.yml stop postgres

db-destroy:
	docker compose -f ../docker-compose.yml down -v

db-logs:
	docker compose -f ../docker-compose.yml logs -f postgres

up:
	docker compose -f ../docker-compose.yml up -d

app-up:
	docker compose -f ../docker-compose.yml up -d mms-service

down:
	docker compose -f ../docker-compose.yml down

logs:
	docker compose -f ../docker-compose.yml logs -f mms-service

build:
	mvn clean package

test:
	mvn test

verify:
	mvn verify

clean:
	mvn clean

migrate-dry-run:
	cd .. && mvn -pl mms-service spring-boot:run \
	  -Dspring-boot.run.profiles=migration \
	  -Dspring-boot.run.arguments="--migration.mode=dry-run"

migrate:
	cd .. && mvn -pl mms-service spring-boot:run \
	  -Dspring-boot.run.profiles=migration \
	  -Dspring-boot.run.arguments="--migration.mode=migrate"

migrate-verify:
	cd .. && mvn -pl mms-service spring-boot:run \
	  -Dspring-boot.run.profiles=migration \
	  -Dspring-boot.run.arguments="--migration.mode=verify"
