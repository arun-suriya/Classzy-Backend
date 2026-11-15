# Classzy backend (Spring Boot 4.1 · Java 21 · PostgreSQL · Maven)

## 1. One-time setup

1. Install **JDK 21** and check `java -version` prints 21.
2. Start PostgreSQL 16+ and create the database:
   - Docker: `docker compose up -d` (uses the included `docker-compose.yml`, creates DB `classzy`), **or**
   - your own PostgreSQL: `CREATE DATABASE classzy;`
3. In IntelliJ: **File → Open**, select this folder's `pom.xml`, choose **Open as Project**. Wait for Maven to download dependencies.
   Check **File → Project Structure → SDK = 21**.

## 2. Run

Run `ClasszyApplication` (green arrow), or in a terminal: `mvn spring-boot:run`.

On first start Flyway creates all tables (`V1__init.sql`) and a seed creates:

| Role | Email | Password |
|---|---|---|
| Super Admin | `superadmin@classzy.local` | `Admin@123` |
| Institution Admin | `admin@demo.school` | `Admin@123` |

Change these (and `JWT_SECRET`) with environment variables for anything other than local development:
`DB_URL, DB_USER, DB_PASSWORD, JWT_SECRET, CORS_ORIGINS, SEED_*` (see `application.yml`).

## 3. Quick test (curl or Postman)

```bash
# 1) sign in
curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"admin@demo.school","password":"Admin@123"}'
# copy "accessToken" from the response

# 2) create an academic year
curl -s -X POST localhost:8080/api/academic-years \
  -H "Authorization: Bearer <TOKEN>" -H 'Content-Type: application/json' \
  -d '{"name":"2025 - 2026","startDate":"2025-04-01","endDate":"2026-03-31","current":true,"active":true}'
```

A Super Admin must also send `X-Institution-Id: 1` (the institution being managed). Institution Admins and Teachers don't - their institution is inside the token.

## 4. Endpoints (all under `/api`, all need `Authorization: Bearer <token>` except login)

| Area | Endpoints |
|---|---|
| Auth | `POST /auth/login` · `GET /auth/me` |
| Institutions (Super Admin) | `GET /institutions` · `POST /institutions` |
| Academic years | `GET /academic-years` · `GET /{id}` · `POST` · `PUT /{id}` · `DELETE /{id}` |
| Terms | `GET /terms?academicYearId=` · `POST` · `PUT /{id}` · `DELETE /{id}` |
| Grading | `GET /grading-systems` (with grades) · `POST` · `PUT /{id}` · `DELETE /{id}` · `POST /{id}/grades` · `PUT /{id}/grades/{gradeId}` · `DELETE /{id}/grades/{gradeId}` |
| Subjects | `GET /subjects?academicYearId=` · `POST` · `PUT /{id}` · `DELETE /{id}` |
| Classes | `GET /classes?academicYearId=` · `POST` · `PUT /{id}` · `DELETE /{id}` |
| Sections | `GET /sections?classId=` · `POST` · `PUT /{id}` · `DELETE /{id}` |
| Promotion | `GET /promotion/rules?academicYearId=` · `POST/PUT/DELETE /promotion/rules[/{id}]` · `GET /promotion/preview?academicYearId=` · `PUT /promotion/progressions` |
| Teachers | `GET /teachers` · `POST` · `PUT /{id}` · `DELETE /{id}` |
| Students | `GET /students?classId=&sectionId=` · `POST` · `PUT /{id}` · `DELETE /{id}` |

Reads are allowed for all three roles; creating, changing and deleting needs Super Admin or Institution Admin.

Errors always look like `{"message":"...","errors":{"field":"..."}}` - the frontend shows `message`.

## 5. Rules enforced (same as the screens, in the service **and** the database)

- Academic years of one institution can't overlap; names are unique; only one is "current"; the current year can't be deleted.
- Terms must fall inside their year, can't overlap, and active weightage can't exceed 100%.
- Grade mark ranges in a grading system can't overlap; grade labels are unique.
- Subject codes are unique per academic year; applicable classes are 1-12.
- A class with sections or students can't be deleted; section names are unique per class; a section can't be set below its current student count; a full section refuses new students.
- Every query is filtered by `institution_id`, so one institution can never read another's data.

## 6. Frontend

Copy `frontend/api.ts` to `src/services/api.ts` in the React project and add `.env`:

```
VITE_API_URL=http://localhost:8080/api
```

## 7. Known limits (next steps)

- Promotion "Eligible / Review / Retain" is only a stored decision for now. Automatic evaluation needs the Exams & Results module (marks); until then students show as *Review* unless set manually.
- Refresh tokens, password reset, and "Sign in with Google/Microsoft" (OAuth2) are not included yet.
- No automated tests yet.
