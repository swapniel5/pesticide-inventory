# Run AgroShop locally with Docker

This guide covers the current local Docker demo and how to set up a new isolated demo environment. It does not use the `pesticide_inventory` database.

## Current demo

The current containers use:

- Application container: `agroshop-demo`
- PostgreSQL container: `agroshop-postgres-test`
- Database: `agroshop_test`
- Database user: `agroshop_user`
- Application URL: <http://localhost:8080>
- Health endpoint: <http://localhost:8080/api/health>

The current app login username is `shopowner`. Its password was generated for the running container and shared separately; it is intentionally not stored in this repository.

Check container status:

```bash
docker ps
```

If the containers are stopped, start PostgreSQL first and wait for it to be ready:

```bash
docker start agroshop-postgres-test
docker exec agroshop-postgres-test pg_isready -U agroshop_user -d agroshop_test
```

Then start AgroShop:

```bash
docker start agroshop-demo
```

Check the health endpoint:

```bash
curl http://localhost:8080/api/health
```

Open <http://localhost:8080> in a browser and authenticate with the app login. Spring Security uses HTTP Basic authentication, so the browser may show a username/password prompt.

## Connect to PostgreSQL

Open `psql` inside the database container:

```bash
docker exec -it agroshop-postgres-test psql -U agroshop_user -d agroshop_test
```

Useful SQL commands:

```sql
\dt
SELECT current_database(), version();
\q
```

Connection URLs:

- From the AgroShop container on Docker Desktop for Mac: `jdbc:postgresql://host.docker.internal:5433/agroshop_test?sslmode=disable`
- From a database client on the host: `jdbc:postgresql://localhost:5433/agroshop_test?sslmode=disable`

## Generate credentials for a new isolated demo

Do not run this section if the existing containers are already in use. It creates credentials for a new isolated setup. The files are placed outside the repository with restrictive permissions.

```bash
umask 077
mkdir -p "$HOME/.agroshop"

DB_PASSWORD="$(openssl rand -hex 24)"
APP_USER="shopowner"
APP_PASSWORD="$(openssl rand -hex 18)"

cat > "$HOME/.agroshop/postgres.env" <<EOF
POSTGRES_DB=agroshop_test
POSTGRES_USER=agroshop_user
POSTGRES_PASSWORD=$DB_PASSWORD
EOF

cat > "$HOME/.agroshop/app.env" <<EOF
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://host.docker.internal:5433/agroshop_test?sslmode=disable
DB_USERNAME=agroshop_user
DB_PASSWORD=$DB_PASSWORD
SPRING_SECURITY_USER_NAME=$APP_USER
SPRING_SECURITY_USER_PASSWORD=$APP_PASSWORD
EOF

printf 'AgroShop username: %s\nAgroShop password: %s\n' "$APP_USER" "$APP_PASSWORD"
```

Save the generated app password in a password manager. Do not commit either env file or paste credentials into source files.

Start a new PostgreSQL container with a persistent Docker volume:

```bash
docker run -d --name agroshop-postgres-test \
  -p 127.0.0.1:5433:5432 \
  -v agroshop-postgres-test-data:/var/lib/postgresql/data \
  --env-file "$HOME/.agroshop/postgres.env" \
  postgres:17-alpine
```

Wait for PostgreSQL:

```bash
docker exec agroshop-postgres-test pg_isready -U agroshop_user -d agroshop_test
```

### Initialize a brand-new empty demo database

The production profile uses `ddl-auto: validate`, so the app expects the schema to exist. Only for a brand-new, isolated, empty demo database, initialize the schema once before starting the normal app:

```bash
docker run -d --name agroshop-schema-init \
  --env-file "$HOME/.agroshop/app.env" \
  -e SPRING_JPA_HIBERNATE_DDL_AUTO=update \
  agroshop:demo
```

Check that initialization completed and the app started:

```bash
docker logs --tail 100 agroshop-schema-init
```

After the logs show successful startup, stop and remove only the temporary schema-initialization container:

```bash
docker stop agroshop-schema-init
docker rm agroshop-schema-init
```

Do not use the schema initialization override against an existing business database. The normal application container must use the production profile's `validate` setting.

Start AgroShop normally:

```bash
docker run -d --name agroshop-demo \
  -p 127.0.0.1:8080:8080 \
  --env-file "$HOME/.agroshop/app.env" \
  agroshop:demo
```

Verify:

```bash
docker ps
curl http://localhost:8080/api/health
```

Then open <http://localhost:8080> and use the generated app username and password.
