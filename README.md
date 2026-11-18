# Ayojon

CSE470 event discovery project using Spring MVC and PostgreSQL.

See [the MVC architecture and request flow](docs/mvc-architecture.md) for
the Model, View, and Controller mapping and the current implementation scope.

For an existing database, run the attendance milestone seed and backfill
statements in [schema.sql](src/main/resources/schema.sql#L175) once when
deploying F3. They can be run again safely.

For F4, apply the demo sponsor voucher seed at the end of
[schema.sql](src/main/resources/schema.sql) to an existing database. To try it,
sign in, open a sample event from **Events**, claim the voucher, and check
**Your rewards** on your profile. The demo code is illustrative.
