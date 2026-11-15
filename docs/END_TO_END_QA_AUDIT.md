# AgroShop Milestone 10: End-to-End QA Audit

**Audit type:** Read-only source review  
**Date:** 2026-11-14  
**Scope:** Architecture, financial calculations, inventory, transactions, security, configuration, templates, and automated-test coverage.

## Audit boundaries

This report is based on source and test inspection. The application was not started, no database was connected, no tests were run, and no demo-data or reset scripts were executed. PostgreSQL behavior and the manual SQL scripts therefore remain unverified at runtime.

No credentials or secret values are included in this report.

## 1. Architecture summary

AgroShop is a modular Spring Boot monolith using Java 21, Spring Boot 3.5.7, Gradle, Spring Data JPA, PostgreSQL, Thymeleaf, Bootstrap, and Spring Security. Domain entities cover products, customers, sales and sale items, payments and allocations, and stock movements. Controllers expose browser pages and REST endpoints; services contain transactional business operations; repositories provide JPA queries, projections, and aggregate calculations.

Key source files:

- [SaleService](../src/main/java/com/agroshop/sales/SaleService.java), [PaymentService](../src/main/java/com/agroshop/payment/PaymentService.java), [ProductService](../src/main/java/com/agroshop/product/ProductService.java), and [CustomerService](../src/main/java/com/agroshop/customer/CustomerService.java)
- [DashboardService](../src/main/java/com/agroshop/dashboard/DashboardService.java) and [ReceiptService](../src/main/java/com/agroshop/receipt/ReceiptService.java)
- [SecurityConfiguration](../src/main/java/com/agroshop/security/SecurityConfiguration.java), [application.yml](../src/main/resources/application.yml), and [application-demo.yml](../src/main/resources/application-demo.yml)
- Thymeleaf pages in [templates](../src/main/resources/templates/)

### Routes

- Browser pages: `/`, `/products`, `/inventory`, `/customers`, `/sales`, `/payments`, and `/sales/{id}/receipt`
- REST: `/api/products`, `/api/customers`, `/api/inventory`, and `/api/sales`
- Public health endpoint: `/api/health`
- There is no payment REST API.

## 2. Financial integrity

### Behavior verified by source inspection

- Invoice totals are computed from line quantities and historical unit prices using `BigDecimal`; sale amounts are rounded per line.
- Initial payments are persisted with their invoice allocation during sale creation.
- Subsequent payments are validated against the customer’s completed invoices. Manual allocations must equal the payment; automatic allocation proceeds oldest-first.
- Customer and dashboard calculations include completed sales and valid, customer-matching payment allocations.
- Sale creation and payment recording are transactional. Payment recording locks the customer and their invoices; sale creation locks the customer and products.

Relevant files: [SaleService](../src/main/java/com/agroshop/sales/SaleService.java), [PaymentService](../src/main/java/com/agroshop/payment/PaymentService.java), [PaymentAllocationRepository](../src/main/java/com/agroshop/payment/PaymentAllocationRepository.java), [CustomerService](../src/main/java/com/agroshop/customer/CustomerService.java), and [DashboardService](../src/main/java/com/agroshop/dashboard/DashboardService.java).

### Financial integrity risks

1. Allocation invariants are enforced by services but are not fully enforced by database constraints. Direct SQL or another writer could create allocations whose sum exceeds an invoice or differs from the payment amount.
2. `SaleService` calculates the sale-detail paid amount by summing all allocations. Customer and receipt calculations filter invalid allocations, including allocations whose payment belongs to a different customer. Manually corrupted data could therefore produce conflicting figures between the sale detail, customer profile, dashboard, and receipt.
3. Outstanding calculations generally subtract paid amounts without clamping negative balances. The receipt clamps to zero, but sale and customer projections can expose negative values if persisted allocations are invalid.
4. The sale detail page labels the current allocated total “Initial amount paid,” although the value includes subsequent payments. See [sales/detail.html](../src/main/resources/templates/sales/detail.html).
5. Sale idempotency returns an existing invoice when a request key is repeated, without verifying that the repeated request has the same sale details.

Under supported service operations, the invariants are enforced and financial aggregates are consistent. The risks above concern inconsistent or manually modified persisted data and repeated keys with changed request bodies.

## 3. Inventory audit

### Behavior verified by source inspection

- Sale creation locks product rows, checks available stock, deducts quantities, and records sale movements in the same transaction.
- Stock adjustments lock the product, reject reductions greater than current stock, update stock, and record a movement transactionally.
- Low-stock queries include active products with quantity less than or equal to their threshold.
- Sale quantity, line totals, invoice totals, prices, and stock use `BigDecimal`.

Relevant files: [ProductService](../src/main/java/com/agroshop/product/ProductService.java), [ProductRepository](../src/main/java/com/agroshop/product/ProductRepository.java), [Product](../src/main/java/com/agroshop/product/Product.java), and [SaleService](../src/main/java/com/agroshop/sales/SaleService.java).

### Inventory integrity risks

1. **Concurrent product updates can lose stock changes.** Sale and adjustment operations use pessimistic product locks, but product update/deactivation load the product without that lock. The entity has no `@Version`. A concurrent update can potentially flush stale quantity values over a sale’s stock deduction.
2. **Quantity scale is not uniformly validated.** Product quantity/threshold and stock-adjustment inputs are backed by three-decimal database columns, but the forms/DTOs do not consistently reject values with more than three decimals. Database rounding can silently alter values.
3. **Sale cancellation and stock restoration are not implemented.** `CANCELLED` exists and receipts display it, but there is no controller/service operation to cancel a sale, restore stock, or record a compensating movement. Do not mark sales cancelled directly in the database.
4. The database may require the documented [stock movement constraint migration](../src/main/resources/db/manual/allow-sale-stock-movement.sql) before it accepts `SALE` movements. Hibernate `ddl-auto: update` does not rewrite an existing check constraint.

## 4. Transaction audit

- Sale creation is class-level `@Transactional`. It writes the sale, invoice items, stock changes, stock movements, and any initial payment/allocation in one service operation.
- Payment recording is class-level `@Transactional`. Payment and its allocations are persisted together after customer/invoice validation and locking.
- Product creation and stock movement changes are class-level `@Transactional`.
- Integration tests include rollback scenarios for insufficient stock and payment-allocation failure.

No service-level partial-write defect was confirmed by source inspection. The principal transaction-adjacent risk is the stale product update race described above; pessimistic locks do not protect against a concurrent writer that does not acquire the lock.

## 5. Security and configuration

- Only `/api/health` is explicitly public; all other routes require authentication.
- HTTP Basic is configured. CSRF is not disabled, so Spring Security’s default CSRF protection applies to mutating requests.
- There is no application-owned user store, explicit role model, or defined owner password policy. If credentials are not configured externally, Spring Boot’s generated default credential is logged at startup.
- The main [application configuration](../src/main/resources/application.yml) contains a hard-coded database password fallback. The value is intentionally omitted here. Remove the fallback and rotate the credential if it has been used.
- `ddl-auto: update` is enabled. Versioned migrations and a PostgreSQL-backed integration suite were not found.
- Demo WhatsApp suppression depends on a marker stored in editable customer notes. Editing the notes can remove the marker and weaken the protection. A dedicated immutable demo-data flag would be safer.

These settings may be suitable for a local demo but need remediation before production use.

## 6. Test coverage analysis

| Module | Existing tests | Missing tests | Risk | Recommendation |
|---|---|---|---|---|
| Foundation/config | Health endpoint test | PostgreSQL schema, startup, and migration checks | Medium | Add a PostgreSQL-backed startup/migration test |
| Products/inventory | Product service/repository and UI tests cover opening stock, adjustments, low stock, and insufficient stock | Concurrent product edit vs. sale; quantity precision; full stock-ledger reconciliation | **High** | Lock or version product updates; add precision and concurrency tests |
| Sales | Integration tests cover totals, initial/partial payments, rollback, idempotency, overselling, and price/name snapshots | Cancellation/restoration; changed payload with reused request key | **High** | Define cancellation semantics and verify idempotency payloads |
| Payments | Integration tests cover full/partial payment, multi-invoice allocation, validation, rollback, history, and concurrency | PostgreSQL lock behavior; database corruption/constraint cases | Medium | Test on PostgreSQL and strengthen persistence-level integrity |
| Customer balances | Tests cover empty, paid/partial, multiple sales/payments, cancelled sales, and customer isolation | Overallocated/corrupt records and concurrent update cases | Medium | Add consistency checks for invalid persisted allocations |
| Dashboard | Integration tests cover totals, ordering, low stock, top customers, empty state, and profile consistency | PostgreSQL native-query execution | Medium | Run dashboard queries against PostgreSQL |
| Receipts/WhatsApp | Tests cover payment states, multiple payments, cancellation display, phone normalization, encoding, and demo suppression | Editable demo-marker behavior | Low–Medium | Use an immutable demo marker and test edits |
| Security | Health test checks the public endpoint; MVC tests use authenticated users and CSRF tokens | Unauthenticated route coverage, auth failure behavior, roles, credential policy | **High** | Add security-focused MVC tests and define production authentication |
| Demo data | H2 tests cover idempotency, reconciliation, and safe reset; guard tests cover target rejection | PostgreSQL execution of seed/migration scripts | Medium | Validate scripts on a disposable PostgreSQL instance |

Integration tests inspected use H2. No tests were run during this audit.

## 7. Findings by priority

### Critical

No critical runtime defect was confirmed from source inspection alone.

### High priority

1. Remove the hard-coded database password fallback and rotate it if it may have been used.
2. Prevent stale product updates from overwriting concurrent stock deductions.
3. Decide whether sale cancellation is supported. If it is, implement cancellation and stock restoration transactionally with movement history.
4. Define production authentication and add tests for protected routes, login behavior, and authorization.

### Medium priority

1. Align sale-detail allocation calculations with customer/receipt definitions and correct the “Initial amount paid” label.
2. Enforce quantity precision before persistence.
3. Compare repeated sale request keys against the submitted payload.
4. Add database-level safeguards or consistency checks for allocations.
5. Replace ad hoc schema evolution with versioned migrations and add PostgreSQL-backed integration coverage.
6. Replace the editable notes-based demo marker with an immutable marker.

## 8. Recommended remediation sequence

1. Address credential handling and define the intended authentication model.
2. Fix product update/deactivation concurrency and add race-condition tests.
3. Decide and implement the sale-cancellation policy, or remove unsupported cancellation pathways.
4. Unify paid/outstanding calculations and add allocation consistency checks.
5. Enforce precision limits and verify the inventory ledger invariant.
6. Add sale idempotency payload comparison.
7. Add PostgreSQL integration coverage for migrations, locking, native queries, and the demo scripts.

No source or database changes were made as part of this audit.
