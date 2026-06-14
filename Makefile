.PHONY: help up down logs build test verify clean ui-install ui-build ui-dev

help:
	@echo "MMS standalone commands:"
	@echo "  make up         Start PostgreSQL, MMS API, and MMS UI"
	@echo "  make down       Stop the standalone stack"
	@echo "  make logs       Follow stack logs"
	@echo "  make build      Build backend and frontend"
	@echo "  make test       Run backend tests"
	@echo "  make verify     Run backend verification and frontend build"
	@echo "  make clean      Clean backend and frontend build output"

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
