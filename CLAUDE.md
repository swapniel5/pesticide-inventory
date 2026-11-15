# Project Engineering Standards

## Technology
- Java 21
- Spring Boot 3.5.7
- Gradle
- PostgreSQL
- Spring Data JPA
- Spring Security
- Thymeleaf

## Architecture
- Feature-based modular monolith.
- Keep controllers responsible for HTTP handling.
- Keep business logic in services.
- Keep persistence logic in repositories.
- Separate DTOs from JPA entities.
- Avoid unnecessary cross-feature dependencies.

## Security
- Enforce server-side authorization.
- Validate all external input.
- Protect sensitive data.
- Never hardcode credentials.
- Review CSRF and session security.

## Database
- Review transaction boundaries.
- Avoid N+1 queries.
- Use appropriate indexes.
- Protect data integrity.
- Test PostgreSQL-specific behavior.

## Testing
- JUnit 5 and Mockito.
- Test business rules and negative scenarios.
- Include integration tests.
- Avoid relying exclusively on H2 for PostgreSQL-specific behavior.

## Review policy
- Do not modify code during reviews.
- Report exact file paths and line numbers.
- Prioritize real defects.
- Do not invent findings.
- Require approval before implementing fixes.
