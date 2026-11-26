.PHONY: db-up db-down backend-run backend-test frontend-install frontend-run frontend-test dev-up

db-up:
	docker compose up -d postgres

db-down:
	docker compose down

backend-run:
	cd backend && mvn spring-boot:run

backend-test:
	cd backend && mvn test

frontend-install:
	cd frontend && npm install

frontend-run:
	cd frontend && npm run dev

frontend-test:
	cd frontend && npm test

dev-up: db-up
	@echo "PostgreSQL started. Run 'make backend-run' and 'make frontend-run' in separate terminals."
