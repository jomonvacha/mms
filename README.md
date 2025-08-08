# Member Management System API

A comprehensive Enterprise-Grade Member Management System built with Spring Boot 3.3.x, Java 21, PostgreSQL, and JWT authentication.

## Features

- **JWT Authentication** with access and refresh tokens
- **Role-based Access Control** (USER, MODERATOR, ADMIN, MEMBER, MANAGER)
- **Member Management** with CRUD operations
- **User Registration and Authentication**
- **PostgreSQL Database** with JPA/Hibernate and connection pooling
- **RESTful API** with proper HTTP status codes
- **Enterprise-Grade Error Handling** with structured error responses and trace IDs
- **Comprehensive Logging** with file rotation and structured JSON logging
- **Input Validation** with detailed validation error responses
- **Pagination and Sorting** support
- **Search Functionality**
- **Performance Optimizations** with compression and caching headers
- **Health Checks and Metrics** via Spring Boot Actuator

## Technology Stack

- **Spring Boot 3.3.2** (Latest LTS)
- **Java 21** (Latest LTS)
- **PostgreSQL** (Primary Database with HikariCP connection pooling)
- **Spring Security 6.x** with JWT
- **Spring Data JPA** with Hibernate
- **Maven** for dependency management
- **BCrypt** for password encryption
- **Logback** with structured logging
- **Docker & Docker Compose** for containerization

## Prerequisites

- Java 21 or higher
- PostgreSQL 13+ 
- Maven 3.6+
- Docker (optional)

## Setup Instructions

### Option 1: Quick Start with Docker (Recommended)

#### Prerequisites
- Docker and Docker Compose installed
- At least 2GB of available RAM

#### Steps
1. **Clone the repository:**
```bash
git clone <repository-url>
cd member-management-system
```

2. **Build and run with Docker Compose:**
```bash
# Build the application and start all services
docker-compose up --build

# Or run in detached mode (background)
docker-compose up -d --build
```

3. **Verify the application is running:**
```bash
# Check service status
docker-compose ps

# Check application health
curl http://localhost:8080/api/health

# Check application info
curl http://localhost:8080/api/info
```

4. **View logs:**
```bash
# View all logs
docker-compose logs -f

# View application logs only
docker-compose logs -f app

# View database logs only
docker-compose logs -f postgres
```

5. **Stop the application:**
```bash
# Stop all services
docker-compose down

# Stop and remove volumes (WARNING: This will delete all data)
docker-compose down -v
```

### Option 2: Local Development Setup

#### Prerequisites
- Java 21 or higher
- PostgreSQL 13+
- Maven 3.6+

#### Database Setup
1. **Install PostgreSQL** and create database:
```sql
-- Connect to PostgreSQL as superuser
CREATE DATABASE member_management_db;
CREATE USER mms_user WITH PASSWORD 'your_secure_password';
GRANT ALL PRIVILEGES ON DATABASE member_management_db TO mms_user;
```

2. **Update database configuration** in `src/main/resources/application.yml`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/member_management_db
spring.datasource.username=mms_user
spring.datasource.password=your_secure_password
```

#### Application Setup
1. **Clone and build:**
```bash
git clone <repository-url>
cd member-management-system

# Build the application
mvn clean install
```

2. **Run the application:**
```bash
# Run with Maven
mvn spring-boot:run

# Or run the JAR file
java -jar target/member-management-system-0.0.1-SNAPSHOT.jar
```

3. **Verify the application:**
```bash
# Check health
curl http://localhost:8080/api/health

# Check public test endpoint
curl http://localhost:8080/api/test/all
```

### Option 3: Production Docker Setup

For production deployments, use environment-specific configurations:

1. **Create production environment file (`.env.prod`):**
```env
# Database Configuration
POSTGRES_DB=member_management_db
POSTGRES_USER=mms_user
POSTGRES_PASSWORD=your_very_secure_password

# Application Configuration
JWT_SECRET=your_256_bit_secret_key_here_make_it_very_secure_and_random
JWT_EXPIRATION=86400000
JWT_REFRESH_EXPIRATION=604800000

# Server Configuration
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=prod
```

2. **Create production Docker Compose file (`docker-compose.prod.yml`):**
```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15
    container_name: mms-postgres-prod
    restart: always
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres_data_prod:/var/lib/postgresql/data
      - ./backups:/backups
    ports:
      - "5432:5432"
    networks:
      - mms-network-prod

  app:
    image: mms-app:latest
    container_name: mms-app-prod
    restart: always
    depends_on:
      - postgres
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      - SPRING_DATASOURCE_USERNAME=${POSTGRES_USER}
      - SPRING_DATASOURCE_PASSWORD=${POSTGRES_PASSWORD}
      - APP_JWT_SECRET=${JWT_SECRET}
    volumes:
      - app_logs_prod:/app/logs
    networks:
      - mms-network-prod

volumes:
  postgres_data_prod:
  app_logs_prod:

networks:
  mms-network-prod:
    driver: bridge
```

3. **Deploy to production:**
```bash
# Load environment variables and deploy
docker-compose --env-file .env.prod -f docker-compose.prod.yml up -d
```

## API Endpoints

### Authentication Endpoints

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| POST | `/api/auth/signup` | Register new user | Public |
| POST | `/api/auth/signin` | User login | Public |
| POST | `/api/auth/refresh` | Refresh JWT token | Public |

### Member Management Endpoints

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| POST | `/api/members` | Create new member | ADMIN, MODERATOR |
| GET | `/api/members` | Get all members (paginated) | MODERATOR, ADMIN |
| GET | `/api/members/{id}` | Get member by ID | USER, MODERATOR, ADMIN |
| GET | `/api/members/membership/{membershipId}` | Get member by membership ID | USER, MODERATOR, ADMIN |
| GET | `/api/members/user/{userId}` | Get member by user ID | USER, MODERATOR, ADMIN |
| GET | `/api/members/status/{status}` | Get members by status | MODERATOR, ADMIN |
| GET | `/api/members/type/{type}` | Get members by type | MODERATOR, ADMIN |
| GET | `/api/members/search?keyword={keyword}` | Search members | MODERATOR, ADMIN |
| PUT | `/api/members/{id}` | Update member | ADMIN, MODERATOR |
| DELETE | `/api/members/{id}` | Delete member | ADMIN |
| PUT | `/api/members/{id}/activate` | Activate member | ADMIN, MODERATOR |
| PUT | `/api/members/{id}/deactivate` | Deactivate member | ADMIN, MODERATOR |
| GET | `/api/members/stats/active-count` | Get active members count | MODERATOR, ADMIN |

### Test Endpoints

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| GET | `/api/test/all` | Public content | Public |
| GET | `/api/test/user` | User content | USER, MODERATOR, ADMIN |
| GET | `/api/test/mod` | Moderator content | MODERATOR |
| GET | `/api/test/admin` | Admin content | ADMIN |

## API Usage Examples

### 1. User Registration

```bash
curl -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{
    "username": "johndoe",
    "email": "john@example.com",
    "password": "password123",
    "firstName": "John",
    "lastName": "Doe",
    "phoneNumber": "1234567890"
  }'
```

### 2. User Login

```bash
curl -X POST http://localhost:8080/api/auth/signin \
  -H "Content-Type: application/json" \
  -d '{
    "username": "johndoe",
    "password": "password123"
  }'
```

Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "id": 1,
  "username": "johndoe",
  "email": "john@example.com",
  "roles": ["ROLE_USER"]
}
```

### 3. Create Member (requires ADMIN/MODERATOR role)

```bash
curl -X POST http://localhost:8080/api/members \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "userId": 1,
    "membershipType": "PREMIUM",
    "membershipStartDate": "2024-01-01",
    "membershipEndDate": "2024-12-31",
    "notes": "Premium member with full access"
  }'
```

### 4. Get All Members with Pagination

```bash
curl -X GET "http://localhost:8080/api/members?page=0&size=10&sortBy=id&sortDir=asc" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### 5. Search Members

```bash
curl -X GET "http://localhost:8080/api/members/search?keyword=john&page=0&size=10" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

## Data Models

### User Entity
- id (Long)
- username (String, unique)
- email (String, unique)
- password (String, encrypted)
- firstName (String)
- lastName (String)
- phoneNumber (String)
- active (Boolean)
- roles (Set<Role>)
- createdAt, updatedAt (LocalDateTime)

### Member Entity
- id (Long)
- membershipId (String, unique, auto-generated)
- user (User, OneToOne)
- membershipType (BASIC, PREMIUM, VIP, CORPORATE, STUDENT, SENIOR)
- status (ACTIVE, INACTIVE, SUSPENDED, EXPIRED, PENDING, CANCELLED)
- membershipStartDate, membershipEndDate (LocalDate)
- notes (String)
- isActive (Boolean)
- createdAt, updatedAt (LocalDateTime)

### Role Entity
- id (Integer)
- name (ERole: ROLE_USER, ROLE_MODERATOR, ROLE_ADMIN, ROLE_MEMBER, ROLE_MANAGER)

## Enterprise-Grade Error Handling

The application implements comprehensive error handling with:

### Structured Error Responses
```json
{
  "timestamp": "2024-08-07T10:30:45.123",
  "status": 400,
  "error": "Bad Request",
  "error_code": "VALIDATION_FAILED",
  "message": "Input validation failed",
  "details": "One or more fields have validation errors",
  "path": "/api/members",
  "trace_id": "A1B2C3D4E5F6G7H8",
  "validation_errors": [
    {
      "field": "email",
      "rejected_value": "invalid-email",
      "message": "Please provide a valid email address",
      "code": "Email"
    }
  ]
}
```

### Exception Types Handled
- **Business Rule Violations** - Custom business logic errors
- **Resource Not Found** - When requested resources don't exist
- **Duplicate Resources** - When creating resources that already exist
- **Validation Errors** - Input validation failures with field-level details
- **Authentication Failures** - Login and token validation errors
- **Authorization Failures** - Access denied scenarios
- **Database Errors** - SQL and constraint violations
- **HTTP Errors** - Method not allowed, media type not supported, etc.

### Logging and Tracing
- **Trace IDs** for request correlation across logs
- **Structured Logging** with JSON format in production
- **Log Rotation** with size and time-based policies
- **Separate Error Logs** for easier monitoring
- **MDC (Mapped Diagnostic Context)** for request tracing

## Security Features

- **JWT Token Authentication** with configurable expiration
- **Refresh Token** mechanism for extended sessions
- **Role-based Access Control** with method-level security
- **Password Encryption** using BCrypt
- **CORS Configuration** for cross-origin requests
- **Input Validation** with Bean Validation
- **SQL Injection Protection** via JPA/Hibernate
- **Structured Error Responses** without sensitive information exposure

## Error Handling

The API returns appropriate HTTP status codes:
- `200 OK` - Successful requests
- `201 Created` - Resource created successfully
- `400 Bad Request` - Invalid input or business logic errors
- `401 Unauthorized` - Authentication required
- `403 Forbidden` - Insufficient permissions
- `404 Not Found` - Resource not found
- `409 Conflict` - Resource conflicts (duplicates, constraints)
- `422 Unprocessable Entity` - Validation errors
- `500 Internal Server Error` - Server errors

Each error response includes:
- Timestamp and HTTP status
- Error code for programmatic handling
- Human-readable message
- Detailed validation errors (when applicable)
- Trace ID for debugging
- Request path information

## Production Considerations

1. **Security:**
   - Change JWT secret to a secure 256-bit key
   - Use environment variables for sensitive configuration
   - Enable HTTPS
   - Configure proper CORS origins

2. **Database:**
   - Use connection pooling
   - Configure proper database indexes
   - Set up database backups

3. **Monitoring:**
   - Enable Spring Boot Actuator endpoints
   - Set up logging and monitoring
   - Configure health checks

4. **Performance:**
   - Implement caching where appropriate
   - Optimize database queries
   - Configure proper pagination limits

## Testing

Run tests with:
```bash
mvn test
```

The application includes H2 in-memory database for testing.

## Monitoring and Health Checks

### Application Health
```bash
# Basic health check
curl http://localhost:8080/api/health

# Detailed application info
curl http://localhost:8080/api/info

# Actuator endpoints (when enabled)
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/info
curl http://localhost:8080/actuator/metrics
```

### Docker Health Monitoring
```bash
# Check container health
docker ps

# View health check logs
docker inspect mms-app | grep -A 10 -B 5 "Health"

# Monitor resource usage
docker stats mms-app mms-postgres
```

### Log Monitoring
```bash
# Application logs
docker-compose logs -f app

# Database logs
docker-compose logs -f postgres

# Error logs only
docker-compose logs -f app | grep ERROR

# Tail specific number of lines
docker-compose logs --tail=100 app
```

## Troubleshooting

### Common Issues

#### 1. Application Won't Start
```bash
# Check if ports are already in use
netstat -tlnp | grep :8080
netstat -tlnp | grep :5432

# On macOS, use lsof instead
lsof -i :8080
lsof -i :5432

# Check Docker container logs
docker-compose logs app
docker-compose logs postgres
```

#### 2. Database Connection Issues
```bash
# Verify PostgreSQL is running
docker-compose exec postgres pg_isready -U postgres

# Connect to database manually
docker-compose exec postgres psql -U postgres -d member_management_db

# Check database tables
docker-compose exec postgres psql -U postgres -d member_management_db -c "\dt"
```

#### 3. JWT Token Issues
- Ensure the JWT secret is at least 256 bits (32+ characters)
- Check token expiration times in configuration
- Verify token format in Authorization header: `Bearer <token>`
- Clear browser cache/tokens if testing in browser

#### 4. Permission Issues
```bash
# Fix file permissions (Linux/Mac)
sudo chown -R $USER:$USER .
chmod +x mvnw

# For logs directory in Docker
mkdir -p logs
chmod 755 logs
```

#### 5. Memory Issues
```bash
# Increase Docker memory limits
docker-compose down
# Edit docker-compose.yml to add memory limits
docker-compose up -d

# For local development, increase JVM memory
export JAVA_OPTS="-Xmx1g -Xms512m"
mvn spring-boot:run
```

#### 6. Build Issues
```bash
# Clean and rebuild
mvn clean install

# Skip tests if needed
mvn clean install -DskipTests

# Force update dependencies
mvn clean install -U
```

### Performance Tuning

#### Database Optimization
```sql
-- Connect to database
docker-compose exec postgres psql -U postgres -d member_management_db

-- Check database size
SELECT pg_size_pretty(pg_database_size('member_management_db'));

-- Check table sizes
SELECT schemaname,tablename,attname,n_distinct,correlation FROM pg_stats;

-- Analyze query performance
EXPLAIN ANALYZE SELECT * FROM users WHERE username = 'testuser';
```

#### Application Optimization
```bash
# Enable JFR (Java Flight Recorder) for profiling
java -XX:+FlightRecorder -XX:StartFlightRecording=duration=60s,filename=app-profile.jfr -jar target/member-management-system-0.0.1-SNAPSHOT.jar

# Monitor JVM metrics via Actuator
curl http://localhost:8080/actuator/metrics/jvm.memory.used
curl http://localhost:8080/actuator/metrics/http.server.requests
```

### Environment-Specific Configuration

#### Development Environment
```yaml
# src/main/resources/application-dev.yml
logging:
  level:
    "com.roots.mms": DEBUG
spring:
  jpa:
    show-sql: true
management:
  endpoints:
    web:
      exposure:
        include: "*"
```

#### Production Environment
```yaml
# src/main/resources/application-prod.yml
logging:
  level:
    "com.roots.mms": WARN
spring:
  jpa:
    show-sql: false
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
server:
  error:
    include-stacktrace: never
```

#### Test Environment
```yaml
# src/test/resources/application-test.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
  jpa:
    hibernate:
      ddl-auto: create-drop
logging:
  level:
    "com.roots.mms": DEBUG
```

## Security Best Practices

### Production Security Checklist
- [ ] Change default JWT secret to a secure 256-bit key
- [ ] Use environment variables for sensitive configuration
- [ ] Enable HTTPS/TLS in production
- [ ] Configure proper CORS origins (not "*")
- [ ] Set up database connection encryption
- [ ] Implement rate limiting
- [ ] Configure proper firewall rules
- [ ] Set up log monitoring and alerting
- [ ] Regular security updates for dependencies
- [ ] Implement database backup strategy

### Environment Variable Security
```bash
# Generate secure JWT secret
openssl rand -hex 32

# Use strong database passwords
openssl rand -base64 32
```

## FAQ

### Q: How do I reset the database?
```bash
# Stop containers and remove volumes
docker-compose down -v

# Start fresh
docker-compose up -d
```

### Q: How do I change the default admin user?
The application creates a default admin user on startup. You can:
1. Change the credentials in `DataInitializer.java`
2. Use the API to create new admin users
3. Use database scripts to modify existing users

### Q: How do I enable debug logging?
```bash
# For Docker
# For Docker
docker-compose exec app sh -c "echo 'logging.level.com.roots.mms: DEBUG' >> /app/application.yml"
docker-compose restart app

# For local development
export SPRING_PROFILES_ACTIVE=dev
mvn spring-boot:run
```

### Q: How do I backup the database?
```bash
# Create backup
docker-compose exec postgres pg_dump -U postgres member_management_db > backup.sql

# Restore backup
docker-compose exec -T postgres psql -U postgres member_management_db < backup.sql
```

### Q: How do I scale the application?
```bash
# Scale app containers (load balancer required)
docker-compose up -d --scale app=3

# For production, use Docker Swarm or Kubernetes
```

## License

This project is licensed under the MIT License.
