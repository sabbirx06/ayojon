# Ayojon MVC architecture

The CSE470 Week 4 MVC slides separate user interaction (Controller), data
handling (Model), and presentation (View). Ayojon uses these responsibilities
with Spring MVC JSON controllers and browser-rendered HTML views.

| Part | Project files | Responsibility |
| --- | --- | --- |
| Model | `model/User.java`, `model/UserRepository.java`, `model/Attendance.java`, `model/AttendanceRepository.java` | Represent account and attendance data and execute parameterized database queries. Password hashes stay out of JSON responses. |
| Controller | `controller/AuthController.java`, `controller/AttendanceController.java` | Handle authentication and attendance URLs, validate input, call the model, and return response data or HTTP errors. |
| View | `resources/static/index.html`, `styles.css`, `assets/js/auth.js`, `assets/js/profile.js` | Display the interface, submit user actions, and render controller responses. |
| Routing and security | Spring MVC annotations, `config/SecurityConfig.java` | Route requests, enforce CSRF, authenticate sessions, and handle login/logout. Authentication reads accounts through the same model repository. |

```mermaid
sequenceDiagram
    actor User
    participant View as Browser HTML/CSS/JS
    participant Controller as AuthController
    participant Model as UserRepository / User
    participant DB as PostgreSQL
    User->>View: Open profile
    View->>Controller: GET /api/auth/me (session cookie)
    Controller->>Model: findByEmail(authenticated email)
    Model->>DB: Parameterized SELECT
    DB-->>Model: Account row
    Model-->>Controller: User
    Controller-->>View: JSON profile (no password hash)
    View-->>User: Render account details
```

Signup follows the same direction: the view submits JSON and a CSRF token,
the controller validates and normalizes input and hashes the password, and
the model inserts the account. Spring Security handles login and logout;
its login lookup uses `UserRepository` rather than its own SQL.

The slides' student example (4.2, pages 10-22) renders an HTML template on
the server. This project deliberately retains browser rendering and its
existing dependencies: the controller returns JSON, and JavaScript updates
the HTML view. It does not use server-side templates. Confirm this rendering
choice with the instructor if the assignment requires that exact example.

The backend implements accounts and the attendance foundation. Event discovery, saved items,
planner, organizer, and admin screens remain UI previews; database tables
alone do not make those features implemented MVC flows. Add their model and
controller code when implementing those features.

Attendance endpoints:

| Method and URL | Access | Response |
| --- | --- | --- |
| `POST /api/attendance/check-in` with `{"eventId":12}` | Session and CSRF token | Created attendance (`201`), duplicate (`409`), or invalid/missing reference (`400`). |
| `GET /api/attendance/me` | Session | Current user's attendance, newest first. |
| `GET /api/attendance/events/{eventId}/status` | Session | `{"checkedIn":true}` or `false`. |
| `GET /api/attendance/events/{eventId}/count` | Public | `{"eventId":12,"attendanceCount":4}`; zero if no matching attendance exists. |

The controller resolves the user ID from the authenticated account, never from
client input. The repository inserts directly and handles the database's unique
constraint to prevent duplicate check-ins, including concurrent requests. PostgreSQL
generates the attendance ID and timestamp; no table or column changes are needed.
This foundation records self check-ins; it does not yet verify physical attendance.

No schema migration or new dependency is needed. Existing `/api/auth/*`
URLs, static asset URLs, hash navigation, signup rules, and session security
remain compatible.

Keep `spring.jpa.hibernate.ddl-auto=none` and `spring.sql.init.mode=never` in
your ignored local `application.properties` so startup cannot initialize or
update the live schema. Do not commit datasource credentials.

Run `./mvnw test` (Windows: `.\mvnw.cmd test`). `AuthControllerTests` and
`AttendanceControllerTests` use mocked repositories with real Spring MVC and
security filters. Repository unit tests mock JDBC; these tests create no database
rows. The existing application context test uses the locally configured datasource.
