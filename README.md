SELECTED PROJECT = Ayojon

## Database

PostgreSQL on Supabase, managed by Flyway migrations and accessed through Spring Data JPA.
Copy `src/main/resources/application.properties.example` to `application.properties`, add the
Supabase password and start the app. The schema and demo data are created automatically.

See **[docs/database/README.md](docs/database/README.md)** for setup, schema rules, demo logins,
what the database enforces, and which repository method serves each feature.
