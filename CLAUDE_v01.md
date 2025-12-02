Ich bin die Lambda9

## Important
- ALL instructions within this document MUST BE FOLLOWED, these are not optional unless explicitly stated.
- ASK FOR CLARIFICATION If you are uncertain of any of thing within the document.
- DO NOT edit more code than you have to.
- DO NOT WASTE TOKENS, be succinct and concise.

## Project Overview

Ready2Race is a monorepo containing a sports event management system with:
- **Backend**: Kotlin REST API using Ktor framework
- **Frontend**: React TypeScript SPA using Vite
- **Database**: PostgreSQL with Flyway migrations and JOOQ for type-safe SQL

## Essential Commands

### Backend Development

```bash
# Start local databases (required for development)
cd backend && docker compose up -d

# Build backend
./mvnw clean package

# Run tests
./mvnw test

# Build Docker image
./build.sh

# Generate JOOQ classes from database schema
./mvnw jooq:generate
```

### Frontend Development

```bash
# Install dependencies
cd frontend && npm install

# Start development server (port 5123)
npm run dev

# Generate TypeScript API client from OpenAPI spec
npm run generate

# Run linting
npm run lint

# Build for production
npm run build

# Build for test environment
npm run testbuild
```

## Architecture Overview

### Backend Architecture

The backend follows Domain-Driven Design with a clear layered architecture:

- **Domain Structure** (`backend/src/main/kotlin/de/lambda9/ready2race/backend/app/`):
  - Each domain (appuser, auth, club, competition, event, etc.) contains:
    - `boundary/`: Service layer with API endpoints
    - `control/`: Repository layer for database access
    - `entity/`: Domain models and DTOs

- **Core Infrastructure**:
  - `kio/`: Custom Kotlin I/O extensions for functional error handling
  - `database/`: Database initialization and connection management
  - `plugins/`: Ktor plugin configuration (routing, serialization, etc.)
  - `security/`: Password hashing with Argon2
  - `validation/`: Input validation framework

- **Key Patterns**:
  - All database operations use JOOQ for type-safe SQL
  - Error handling uses KIO monad pattern
  - API endpoints return structured responses via `calls/` utilities

### Frontend Architecture

- **Component Organization** (`frontend/src/`):
  - `api/`: Auto-generated TypeScript client from OpenAPI spec
  - `components/`: Reusable React components organized by domain
  - `contexts/`: React Context providers for state management
  - `pages/`: Route-specific page components
  - `authorization/`: Privilege management system

- **Key Technologies**:
  - TanStack Router for client-side routing
  - Material-UI component library
  - i18next for internationalization (DE/EN)
  - React Hook Form for form management

## Development Workflow

### API-First Development

1. API changes are defined in `backend/src/main/resources/openapi/documentation.yaml`
2. Frontend TypeScript client is generated via `npm run generate`
3. This ensures type safety between backend and frontend

### Database Changes

1. Create new migration in `backend/src/main/resources/db/migration/`
2. Follow Flyway naming convention: `V{version}__{description}.sql`
3. **IMPORTANT**: Views must be created in `afterMigrate.sql`, NOT in regular migration files
   - Drop the view first (if exists)
   - Then create the view
   - This ensures views are recreated after all migrations
4. Run backend to apply migrations automatically
5. Regenerate JOOQ classes if schema changes: `./mvnw jooq:generate`

### Environment Configuration

- Backend: Copy `backend/template.env` to `backend/.env` and configure
- Frontend: Copy `frontend/template.env` to `frontend/.env` and configure
- Different configurations for DEV, STAGING, PROD, TEST environments

## Key Implementation Details

### Authentication & Authorization

- JWT-based authentication
- Privilege system defined in `frontend/src/authorization/privileges.ts`
- Backend validates privileges on each request

### QR Code Integration

- QR codes are used for event participant identification
- Scanner component at `frontend/src/components/qrcode/QrScanner.tsx`
- QR code generation handled by backend

### Internationalization

- Backend: Templates in `backend/src/main/resources/internationalization/`
- Frontend: i18n resources in `frontend/src/i18n/`
- Languages: German (de) and English (en)

### PDF Generation

- Backend generates PDFs using Apache PDFBox
- Templates and utilities in `backend/src/main/kotlin/de/lambda9/ready2race/backend/pdf/`

## Testing

### Backend Testing

- JUnit 5 with Kotlin test support
- Testcontainers for database integration tests
- Run with: `./mvnw test`

### Frontend Testing

- No test runner currently configured
- Consider adding Vitest or Jest for React component testing

## Code Quality

### Backend

- Kotlin compiler provides type safety
- Follow Kotlin coding conventions

### Frontend

- ESLint configuration for code quality
- Prettier for code formatting
- Run `npm run lint` before committing

## Important Notes

- The project uses a functional programming approach in the backend with KIO monads
- Database access is exclusively through JOOQ - avoid raw SQL
- All API changes must be reflected in the OpenAPI specification
- Frontend components should follow Material-UI design patterns
- State management uses React Context API, not Redux

## KIO Framework

This project uses the KIO framework for functional programming in Kotlin. `KIO<R, E, A>` represents a computation that uses environment `R` and either returns a value of type `A` or fails with an error of type `E`.

### KIO Usage Patterns
```kotlin
val x = KIO.ok(5)
val y = KIO.fail(Problem("Bad stuff"))
val db = Jooq.query { selectCount().from(PERSON) }
```

### Architecture: Entity-Control-Boundary (ECB)

Applications are structured by ECB pattern and logical feature/domain:

#### Routes (Boundary)
- Create `routes.kt` per logical domain for HTTP endpoints
- **ALWAYS** ensure input/output matches `documentation.yaml` OpenAPI spec
- **ALWAYS** build pagination when listing entities
- Use `respondComprehension` for KIO-based handlers

```kotlin
fun Routing.personRoutes() {
    get("/persons") {
        call.respondComprehension {
            val query = !call.queryParam("query", optional(string))
            val persons = !PersonService.search(query)
            KIO.ok(persons)
        }
    }
}
```

#### Services (Control)
- Entry points to work with domains
- **NEVER** implement database requests in Services
- **ALWAYS** use `!` to run KIO inside `KIO.comprehension`, except for return values
- **ALWAYS** return `ApiResponse` type when the function is used by a KTOR route

##### ApiResponse Subclasses Usage:
- **`NoData`**: Use when an operation succeeds but returns no data (e.g., DELETE operations, updates without returning the entity)
- **`Dto<T>`**: Use when returning a single entity/object (e.g., GET by ID, CREATE operations that return the created entity)
- **`ListDto<T>`**: Use when returning a simple list without pagination (e.g., dropdown options, small reference data)
- **`Page<T, S>`**: Use when returning paginated lists with sorting capabilities (e.g., large entity lists, search results)
- **`File`**: Use when returning file content (e.g., PDF generation, exports)
- **`Created`**: Use specifically for CREATE operations that only need to return the created entity's ID

```kotlin
object PersonService {
    fun create(person: NewPersonDTO): App<Problem, ApiResponse> = KIO.comprehension {
        val exists = !PersonRepo.exists(person).orDie()
        if (exists) {
            KIO.fail(Problem("Person already exists"))
        } else {
            val newPerson = !PersonRepo.insert(person).orDie()
            KIO.ok(ApiResponse.Dto(newPerson.toDto()))
        }
    }
    
    fun delete(id: UUID): App<Problem, ApiResponse> = KIO.comprehension {
        !PersonRepo.delete(id).orDie()
        KIO.ok(ApiResponse.NoData)
    }
}
```

#### Repositories (Entity)
- **ALWAYS** add all database queries in Repos
- Use JOOQ for type-safe database interactions
- **ALWAYS** put Jooq.query inside a Repo

```kotlin
object PersonRepo {
    fun exists(person: NewPersonDTO): JIO<Boolean> = Jooq.query {
        fetchExists(selectFrom(PERSON).where(PERSON.NAME.eq(person.name)))
    }
}
```

## Common Development Patterns

- **ECB Architecture**: Entity-Control-Boundary per business domain
- **KIO Functional Programming**: All business logic uses KIO for error handling
- **Repository Pattern**: Database access isolated in repository classes
- **Job Queues**: Background processing using database-backed queues
- **Type-safe Database Access**: All queries use JOOQ generated classes
- **OpenAPI-First**: API changes start with specification updates


## Domain Design Considerations

- Think about whether an error in a separate domain needs to be created. Like the "Problem" type from one domain should not be used in another

## Data Modeling Guidelines

- **Data Classes Placement**:
  - Add data classes in the entity package of a domain, not in the service

## Commit Guidelines

- **Contributors**:
  - Don't add claude and anthropic as contributors in commits


## Frontend Development Guidelines

- **Always run `npm run generate`** after modifications on @server/src/main/resources/openapi/documentation.yaml
- **Use generated classes** instead of writing own interfaces/types in frontend code
- **Add cursor-pointer class** to all clickable links/buttons/icons

## TypeScript Guidelines

- **NEVER use `any` type or `as any` casts**:
  - Always use proper types or ask for clarification
  - If a translation key is missing, add it to the translation files
  - If a type is missing, define it properly or ask for help
  - Type safety is critical - no shortcuts with `any`

## Database Migration Guidelines

- Migration filenames have the pattern VyyyyMMddHHmm_description.sql please replace the String between V and __ with the matching timestamp

## Build Verification Guidelines

- **Backend Changes**:
  - After making changes to backend code, ALWAYS verify the build with: `cd backend && ./mvnw clean compile`
  - If build fails, fix the errors before proceeding
  - Check for compilation errors, missing imports, or type mismatches

- **Frontend Changes**:
  - After making changes to frontend code, ALWAYS verify the build with: `cd frontend && npm run build`
  - If build fails, fix TypeScript errors or missing dependencies before proceeding
  - Also run `npm run lint` to ensure code quality standards are met