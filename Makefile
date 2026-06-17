.PHONY: help start stop up down logs build test verify clean ui-install ui-build ui-dev

help:
	@echo "MMS standalone commands:"
	@echo "  make start      Run service + UI locally (./start.sh)"
	@echo "  make stop       Stop the local service + UI (./stop.sh)"
	@echo "  make up         Start PostgreSQL, MMS API, and MMS UI (Docker)"
	@echo "  make down       Stop the standalone stack (Docker)"
	@echo "  make logs       Follow stack logs"
	@echo "  make build      Build backend and frontend"
	@echo "  make test       Run backend tests"
	@echo "  make verify     Run backend verification and frontend build"
	@echo "  make clean      Clean backend and frontend build output"

start:
	./start.sh

stop:
	./stop.sh

up:
	docker compose up --build -d

down:
	docker compose down

logs:
	docker compose logs -f

build:
	mvn -B -ntp package
	npm --prefix mms-ui run build

test:
	mvn -B -ntp test

verify:
	mvn -B -ntp verify
	npm --prefix mms-ui run build

clean:
	mvn -B -ntp clean
	rm -rf mms-ui/dist

ui-install:
	npm --prefix mms-ui ci

ui-build:
	npm --prefix mms-ui run build

ui-dev:
	npm --prefix mms-ui run dev
