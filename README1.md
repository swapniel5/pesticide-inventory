# Pesticide Inventory & Credit Management System

A beginner-friendly Spring Boot modular monolith for a village pesticide shop. Product, inventory, customer, sales/billing, and credit-management workflows are implemented.

The project includes Thymeleaf and Bootstrap 5 (served as a WebJar) for server-rendered dashboard, product, inventory, customer, sales, payment, and receipt pages.

## Requirements

- Java 21
- PostgreSQL

## Configure PostgreSQL

The application reads its JDBC connection from environment variables:

- `DB_URL` — defaults to `jdbc:postgresql://localhost:5432/pesticide_inventory`
- `DB_USERNAME` — required PostgreSQL role
- `DB_PASSWORD` — required role password

Do not put database credentials in project files or commit them to source control.

Shop details printed on receipts are configured with:

- `SHOP_NAME` — defaults to `AgroShop`
- `SHOP_ADDRESS` — optional shop address
- `SHOP_PHONE` — optional shop mobile number

### Local PostgreSQL on macOS with Homebrew

Install and start PostgreSQL 17:

```bash
brew install postgresql@17
brew services start postgresql@17
export PATH="$(brew --prefix postgresql@17)/bin:$PATH"
```

Homebrew may initialize localhost TCP connections with `trust` authentication. For password-checked local connections, edit `$(brew --prefix)/var/postgresql@17/pg_hba.conf` and change the IPv4 and IPv6 `host all all` rules to:

```text
host    all    all    127.0.0.1/32    scram-sha-256
host    all    all    ::1/128         scram-sha-256
```

If the file has localhost `host replication` rules using `trust`, change those to `scram-sha-256` as well. Leave the `local` Unix-socket rules unchanged. Reload PostgreSQL:

```bash
pg_ctl -D "$(brew --prefix)/var/postgresql@17" reload
```

Create a dedicated application role and database. These are one-time commands; skip creation if they already exist:

```bash
psql -d postgres
```

At the `psql` prompt:

```text
CREATE ROLE agroshop_app LOGIN;
\password agroshop_app
CREATE DATABASE pesticide_inventory OWNER agroshop_app;
\q
```

`\password` prompts for the role password without requiring it to be typed into a SQL statement or saved in shell history. Verify the TCP connection; `psql` prompts for the password:

```bash
psql -h 127.0.0.1 -U agroshop_app -d pesticide_inventory \
  -c 'select current_database(), current_user, version();'
```

### Configure and run the application

In the same terminal where you will run the app, set the connection values. The password prompt below keeps the password out of the command history:

```bash
export DB_URL=jdbc:postgresql://127.0.0.1:5432/pesticide_inventory
export DB_USERNAME=agroshop_app
printf 'Database password: '
read -rs DB_PASSWORD
printf '\n'
export DB_PASSWORD
```

Spring Security protects all routes except `/api/health` with HTTP Basic authentication. Configure the shop-owner username and password using environment variables; both are required and no generated/default password is used. The password is BCrypt-encoded in memory:

```bash
export SERVER_ADDRESS=127.0.0.1
export SPRING_SECURITY_USER_NAME=qa
printf 'Application password: '
read -rs SPRING_SECURITY_USER_PASSWORD
printf '\n'
export SPRING_SECURITY_USER_PASSWORD
```

From the project root, start the app:

```bash
./gradlew bootRun
```

Hibernate uses `ddl-auto: update` to create/update the demo tables at startup. This is not a substitute for versioned migrations in production.

### End-to-end validation

Open [http://localhost:8080/products](http://localhost:8080/products) and authenticate with the Spring Security username/password configured above. Then check these flows:

1. Add a product from `/products/new`, including an opening quantity.
2. Open the product, edit its details, and save. Use Inventory—not the product form—to change its quantity.
3. Go to `/inventory` and record a purchase or manual stock adjustment.
4. Reduce stock below the product's low-stock threshold. The product should show a low-stock indicator on the product/inventory pages and appear in `GET /api/products/low-stock`.
5. Try reducing more than the current quantity. The form should show a validation error, the JSON stock-movement endpoint should return HTTP 400, and the quantity should remain unchanged.
6. Confirm the product and movement history persisted in PostgreSQL:

```bash
psql -h 127.0.0.1 -U agroshop_app -d pesticide_inventory
```

At the `psql` prompt:

```sql
SELECT id, name, quantity, low_stock_threshold, active FROM products;
SELECT product_id, movement_type, quantity, remarks, created_at
FROM stock_movements
ORDER BY created_at DESC;
\q
```

The public health endpoint, [http://localhost:8080/api/health](http://localhost:8080/api/health), returns `{"status":"UP"}`. It is a static application check and does not check the database. Verify the PostgreSQL connection from the successful application startup logs (Hikari connection and PostgreSQL version) or with the `psql` command above. API mutation requests are protected by CSRF as well as HTTP Basic; use the Thymeleaf forms for browser-based testing.

All routes other than `/api/health` require HTTP Basic authentication using the configured shop-owner credentials. Keep the password in the environment or a local secret manager; do not put it in project files or commit it.

## Dashboard

Open [http://localhost:8080/](http://localhost:8080/) after authenticating. The dashboard summarizes completed sales, outstanding balances, valid allocated payments, and low-stock products; it also shows recent sales, recent payments, and customers with the highest outstanding balances. Quick actions link to the existing workflows.

The application uses HTTP Basic authentication and preserves the originally requested URL after authentication. Opening `/` first takes the shop owner directly to the dashboard.

The dashboard's **Record payment** action opens `/payments/new`, where an active customer can be selected before continuing to the existing invoice-allocation form.

## Demo dataset

Demo data is generated only when the `demo` profile is active. Its configuration targets the loopback PostgreSQL database `agroshop_demo`; the seeder checks both the active profile and the actual JDBC connection before writing. It refuses remote hosts or other database names. Never point `DEMO_DB_URL` at a production or business database.

Create a separate local database and role (skip commands for objects that already exist):

```text
CREATE ROLE agroshop_app LOGIN;
\password agroshop_app
CREATE DATABASE agroshop_demo OWNER agroshop_app;
```

In the terminal used to start the application, configure the database password and optional application login, then start with the dedicated profile:

```bash
export DB_USERNAME=agroshop_app
printf 'Demo database password: '
read -rs DB_PASSWORD
printf '\n'
export DB_PASSWORD
export SPRING_SECURITY_USER_NAME=qa
printf 'Demo application password: '
read -rs SPRING_SECURITY_USER_PASSWORD
printf '\n'
export SPRING_SECURITY_USER_PASSWORD
./gradlew bootRun --args='--spring.profiles.active=demo'
```

The startup log prints created counts, sales, allocated payments, outstanding credit, low-stock count, and customers with balances. Re-running is idempotent. Demo customer phone values are clearly marked invalid placeholders; the receipt flow also suppresses WhatsApp sharing for customers whose notes carry the demo marker. Product categories and descriptions are not fields in the current `Product` model, so the seeder does not invent or store them.

To remove only registered demo records, stop the running application and run:

```bash
./gradlew bootRun --args='--spring.profiles.active=demo --agroshop.demo-data.reset-only=true'
```

Reset removes demo payment allocations, sale items, payments, sales, stock movements, products, and customers in dependency order. It first checks for non-demo sales or payments linked to demo records and aborts without deleting anything if such references exist. The demo registry is created only after the profile/database guard passes. Never run this profile against production.

### Manual PostgreSQL seed

Alternatively, the SQL scripts in `src/main/resources/db/manual/` can create the same 20 products, 20 customers, 30 invoices, 73 sale items, 40 allocated payments, and 4 low-stock products. They are restricted to a database named `agroshop_demo`, refuse to duplicate marked demo records, and add the IDs to the demo registry so the application can report or safely reset them.

First initialize/update the schema without running the application seeder, then stop the application:

```bash
export DEMO_DB_URL=jdbc:postgresql://127.0.0.1:5432/agroshop_demo
export DB_USERNAME=agroshop_app
printf 'Demo database password: '
read -rs DB_PASSWORD
printf '\n'
export DB_PASSWORD
./gradlew bootRun --args='--spring.profiles.active=demo --agroshop.demo-data.run-on-startup=false'
```

In another terminal, run the stock-movement constraint migration, seed script, and verification script in order:

```bash
psql -h 127.0.0.1 -U agroshop_app -d agroshop_demo -W -v ON_ERROR_STOP=1 \
  -f src/main/resources/db/manual/allow-sale-stock-movement.sql
psql -h 127.0.0.1 -U agroshop_app -d agroshop_demo -W -v ON_ERROR_STOP=1 \
  -f src/main/resources/db/manual/demo-data.sql
psql -h 127.0.0.1 -U agroshop_app -d agroshop_demo -W -v ON_ERROR_STOP=1 \
  -f src/main/resources/db/manual/verify-demo-data.sql
```

Stop the schema-initialization process with Ctrl+C. Start the application normally with the `demo` profile afterward; the populated registry makes the startup seeder recognize the dataset rather than duplicate it. To remove the registered SQL-seeded data, use the `reset-only=true` command above. These scripts do not create a database or role and must never be run against production or the regular business database.

## Product and inventory

The browser UI is available at:

- `/products` — search and browse products
- `/products/new` — create a product and record its opening stock
- `/products/{id}` and `/products/{id}/edit` — view and update product details
- `/inventory` — review current/low stock and record purchases or manual adjustments

The JSON API is rooted at `/api/products` and `/api/inventory`. Product deletion is a soft deactivation, and stock movements are retained in history.

## Customer management

The browser UI is available at:

- `/customers` — search by customer name, mobile number, or village
- `/customers/new` — register a customer
- `/customers/{id}` and `/customers/{id}/edit` — view and update customer details

Customers can be deactivated but are never physically deleted. Indian mobile numbers must contain 10 digits beginning with 6–9, optionally prefixed with `+91`. Customer profiles show sales newest-first, payment history, and completed purchases, valid allocated payments, and outstanding balances calculated from persisted transactions. Cancelled sales and allocations whose payment customer does not match the sale customer are excluded from financial totals.

The JSON API is rooted at `/api/customers`:

- `GET /api/customers?search={term}` — list customers, optionally filtered across name, phone, and village
- `GET /api/customers/{id}` — retrieve one customer
- `POST /api/customers` — create a customer
- `PUT /api/customers/{id}` — update customer details
- `PATCH /api/customers/{id}/deactivate` — deactivate a customer

Customer records are stored in the `customers` table, which the existing Hibernate `ddl-auto: update` setting creates on application startup.

## Sales and billing

The browser UI is available at:

- `/sales` — review completed sales
- `/sales/new` — create an invoice for an active customer and active products
- `/sales/{id}` — view invoice lines, the initial payment, and outstanding balance
- `/sales/{id}/receipt` — print a receipt or open a pre-filled WhatsApp message for manual review and sending

The sale form previews line totals, invoice total, and remaining balance. The server recalculates all amounts from the current product prices using `BigDecimal` and `HALF_UP` rounding to two decimal places. Product stock is locked, checked, deducted, and recorded as `SALE` movements in the same transaction as the invoice and any initial payment. A generated request key prevents a repeated submission from creating a second sale.

Receipts use the stored item name and price snapshots and calculate the paid amount from valid payment allocations. Shop name, address, and phone can be set with `SHOP_NAME`, `SHOP_ADDRESS`, and `SHOP_PHONE`. WhatsApp sharing only opens a pre-filled message for the shop owner to review; it does not send automatically or use the WhatsApp Business API.

The JSON API is rooted at `/api/sales`:

- `GET /api/sales` — list invoices
- `GET /api/sales/{id}` — retrieve an invoice with its customer, item snapshots, paid amount, and outstanding balance
- `POST /api/sales` — create a sale; include a unique `requestKey` in the JSON body for duplicate-submission protection

Example request (replace IDs with active database records):

```json
{
  "customerId": 1,
  "requestKey": "a-unique-client-generated-key",
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 2, "quantity": 1.5 }
  ],
  "amountPaid": 10.00,
  "paymentMethod": "CASH"
}
```

An initial payment of zero does not create a payment or allocation. Later customer payments are recorded using the Credit management and payments workflow below.

Hibernate creates the `sales`, `sale_items`, `payments`, and `payment_allocations` tables and the sale ID sequence on startup. Invoice numbers use the year and sequence-generated sale ID, with a unique database constraint; gaps are expected if a sale transaction rolls back.

#### Update an existing PostgreSQL database for `SALE` movements

The first database schema may have a check constraint that only permits the original stock movement types. Hibernate's `ddl-auto: update` does not rewrite an existing check constraint. Apply this one-time migration to the existing database before saving sales:

```bash
psql -h 127.0.0.1 -U agroshop_app -d pesticide_inventory \
  -v ON_ERROR_STOP=1 -f src/main/resources/db/manual/allow-sale-stock-movement.sql
```

`psql` prompts for the PostgreSQL role password. The migration replaces the existing movement-type check and adds `SALE` to the allowed values; it does not change stock data.

### Verify sales against PostgreSQL

After exporting `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` as described above, start the application and authenticate with the configured Spring Security credentials:

1. Register an active customer and ensure at least two active products have sufficient stock.
2. Visit `/sales/new`, select the customer and products, enter quantities, and verify the preview and initial-payment balance.
3. Confirm the sale. Check the invoice page and ensure product quantities decreased.
4. Repeat the same submission only by resubmitting its existing request key; it should return the existing invoice rather than deduct stock again.
5. Try a quantity greater than available stock and confirm no invoice, payment, or stock movement is committed.
6. Inspect persisted records:

```bash
psql -h 127.0.0.1 -U agroshop_app -d pesticide_inventory
```

```sql
SELECT id, invoice_number, customer_id, total_amount, status, created_at FROM sales;
SELECT sale_id, product_id, product_name_snapshot, quantity, unit_price, line_total FROM sale_items;
SELECT id, customer_id, amount, payment_method, payment_date FROM payments;
SELECT payment_id, sale_id, allocated_amount FROM payment_allocations;
SELECT product_id, movement_type, quantity, remarks FROM stock_movements WHERE movement_type = 'SALE';
\q
```

## Credit management and payments

The browser UI is available at:

- `/payments` — browse payments and filter by customer and date range
- `/payments/new` — choose an active customer before recording a payment
- `/customers/{id}/payments/new` — record a payment for a customer and review invoice allocations
- `/payments/{id}` — view payment details and allocated invoices

Customer profiles include a **Record payment** action, payment history, and a financial summary recalculated from persisted allocations. Each payment must be fully allocated to completed invoices for that same customer. Manual allocation is supported, and the optional oldest-first allocation preview can be reviewed before saving. The server locks the customer and its completed invoices, then validates and persists the payment and all allocations in one transaction. Duplicate form submissions reuse their submission key; payments and allocations are not editable or physically deleted.

No new payment table is introduced. Hibernate's existing `ddl-auto: update` adds `transaction_reference` and a nullable unique `request_key` column to the existing `payments` table. Existing initial-sale payments retain a null request key and remain unchanged. The application should be started once against an existing PostgreSQL database so Hibernate can apply the schema update.

To manually verify with PostgreSQL:

1. Start the application with the configured PostgreSQL environment and authenticate using the existing HTTP Basic credentials.
2. Open a customer profile with an outstanding invoice and select **Record payment**.
3. Enter an amount, date, and method; allocate it manually or select oldest-first allocation. Confirm the amounts sum exactly to the payment before recording.
4. Verify the payment details page, the updated customer balance, and the payment listing filters.
5. Inspect the persisted payment and allocations:

```sql
SELECT id, customer_id, amount, payment_method, payment_date, transaction_reference
FROM payments
ORDER BY id DESC;

SELECT pa.payment_id, s.invoice_number, pa.allocated_amount
FROM payment_allocations pa
JOIN sales s ON s.id = pa.sale_id
ORDER BY pa.payment_id DESC, s.created_at;
```

The automated integration tests use H2; they do not establish that the application connected to PostgreSQL.

## Build and test

```bash
./gradlew clean build
```

## Cloud demo deployment (Render + Neon)

The Docker image runs the `prod` Spring profile. In Render, create a Docker web service from this repository and set its health check path to `/api/health`. Render supplies `PORT`; the application binds to it and defaults to port 8080 for local runs.

Set these environment variables in Render's environment settings. Do not commit their values or put secrets in `render.yaml`:

- `DB_URL` — Neon JDBC URL, for example `jdbc:postgresql://<neon-host>/<database>?sslmode=require`
- `DB_USERNAME` — Neon database role
- `DB_PASSWORD` — Neon role password
- `SPRING_SECURITY_USER_NAME` — shop-owner Basic Auth username
- `SPRING_SECURITY_USER_PASSWORD` — strong shop-owner Basic Auth password

`SPRING_PROFILES_ACTIVE=prod` is set in the Docker image. `PORT` is set by Render. Optional receipt settings are `SHOP_NAME`, `SHOP_ADDRESS`, and `SHOP_PHONE`. Do not set `SERVER_ADDRESS` to `127.0.0.1` on Render.

### Initialize a fresh Neon database

The production profile uses `ddl-auto: validate` and will not create tables. This project has no versioned schema migration tool. For a **new, empty, dedicated Neon database only**, bootstrap the schema once from the existing JPA entities using Hibernate's current `update` mode:

1. Configure the Neon `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and shop-owner variables in a local terminal. Keep the database password out of command history using a hidden prompt.
2. Run the application locally, bound to localhost, with the one-time schema override:

   ```bash
   export SPRING_PROFILES_ACTIVE=prod
   export SERVER_ADDRESS=127.0.0.1
   export SPRING_JPA_HIBERNATE_DDL_AUTO=update
   ./gradlew bootRun
   ```

3. Wait for successful startup, then stop it with Ctrl+C. This should only target the empty Neon database. Never use the override against an existing business database.
4. Apply the existing stock-movement constraint script using Neon host/database details; `psql` prompts for the database password:

   ```bash
   psql -h <neon-host> -U <DB_USERNAME> -d <database> -W \
     -v ON_ERROR_STOP=1 \
     -f src/main/resources/db/manual/allow-sale-stock-movement.sql
   ```

5. Remove the schema override and deploy the Docker image normally:

   ```bash
   unset SPRING_JPA_HIBERNATE_DDL_AUTO
   ```

The Render deployment then validates the existing schema. Keep a backup before any future schema changes and review them explicitly; Hibernate `update` is only a one-time bootstrap convenience here, not a versioned migration process. Do not activate the `demo` profile on Render.

### Local Docker checks

Build and run the same production image locally after Docker is installed:

```bash
docker build -t agroshop-demo .
docker run --rm -p 8080:8080 \
  -e DB_URL='jdbc:postgresql://<neon-host>/<database>?sslmode=require' \
  -e DB_USERNAME='<database-role>' \
  -e DB_PASSWORD \
  -e SPRING_SECURITY_USER_NAME='<shop-owner>' \
  -e SPRING_SECURITY_USER_PASSWORD \
  agroshop-demo
```

The `-e DB_PASSWORD` and `-e SPRING_SECURITY_USER_PASSWORD` options pass values already exported in the shell without placing the values directly in the command. Open `http://localhost:8080/` and authenticate with the shop-owner credentials.

## Project layout

```text
com.agroshop
├── config       # Application configuration
├── security     # Spring Security configuration
├── product      # Product module
├── customer     # Customer management
├── sales        # Sales and billing
├── payment      # Credit management and payment allocation
├── receipt      # Printable receipts and WhatsApp sharing links
├── inventory    # Stock tracking and movement history
├── receipt      # Receipt module (scaffold only)
├── dashboard    # Business summary and recent activity
└── common       # Shared application components, including health check
```

The application is a single deployable Spring Boot project; the module packages are boundaries within the monolith, not separate services.
