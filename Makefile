.PHONY: setup verify run

setup:
	@mkdir -p artifacts evidence docs db/migrations db/seed \
	         src/main/java src/main/resources src/test/java
	@test -f .env.example
	@echo "CDRL starter base preparada. Configura .env localmente cuando corresponda."

verify:
	@bash scripts/verify_base.sh

# make run levanta los contenedores (Postgres + DynamoDB) en background
# y arranca la aplicación. Para detener los contenedores:
#   docker compose down
run:
	@docker compose up -d
	@mvn spring-boot:run
