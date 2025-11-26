# Yushan API Gateway

API Gateway and routing service for Yushan Novel Platform microservices architecture.

## Features

- Single entry point for all microservices
- Service discovery via Eureka
- Request/Response logging
- CORS configuration
- Health monitoring
- Load balancing across service instances
- Service routing and path rewriting

## Architecture

The API Gateway routes requests to the following microservices:

- **User Service** (port 8081) - Authentication & user management
- **Content Service** (port 8082) - Novels & chapters
- **Analytics Service** (port 8083) - Rankings & history
- **Engagement Service** (port 8084) - Comments, reviews & votes
- **Gamification Service** (port 8085) - EXP, Yuan & achievements

## Running Locally

### Prerequisites
- Java 21+
- Maven 3.8+
- Eureka Server running on port 8761
- All microservices running on their respective ports

### Start the Gateway
```bash
./mvnw spring-boot:run
```

The gateway will start on port 8080.

### Build JAR
```bash
./mvnw clean package -DskipTests
java -jar target/api-gateway-1.0.0.jar
```

## API Routes

All requests go through `http://localhost:8080/api/*`

### User Service Routes
| Method | Endpoint | Authentication | Description |
|--------|----------|---------------|-------------|
| POST | `/api/auth/login` | Public | User login |
| POST | `/api/auth/register` | Public | User registration |
| GET | `/api/users/{id}` | Required | Get user profile |
| PUT | `/api/users/{id}` | Required | Update user profile |
| GET | `/api/library` | Required | Get user library |

### Content Service Routes
| Method | Endpoint | Authentication | Description |
|--------|----------|---------------|-------------|
| GET | `/api/novels` | Public | List all novels |
| POST | `/api/novels` | Required | Create novel |
| GET | `/api/novels/{id}` | Public | Get novel details |
| GET | `/api/chapters/{id}` | Required | Get chapter content |
| POST | `/api/chapters` | Required | Create chapter |

### Engagement Service Routes
| Method | Endpoint | Authentication | Description |
|--------|----------|---------------|-------------|
| POST | `/api/comments` | Required | Add comment |
| GET | `/api/comments/{chapterId}` | Public | Get comments |
| POST | `/api/reviews` | Required | Add review |
| GET | `/api/reviews/{novelId}` | Public | Get reviews |
| POST | `/api/votes` | Required | Vote on content |

### Gamification Service Routes
| Method | Endpoint | Authentication | Description |
|--------|----------|---------------|-------------|
| GET | `/api/exp/{userId}` | Required | Get user EXP |
| POST | `/api/exp` | Required | Award EXP |
| GET | `/api/yuan/{userId}` | Required | Get Yuan balance |
| POST | `/api/yuan` | Required | Update Yuan |
| GET | `/api/achievements/{userId}` | Required | Get achievements |

### Analytics Service Routes
| Method | Endpoint | Authentication | Description |
|--------|----------|---------------|-------------|
| GET | `/api/rankings/novels` | Public | Get novel rankings |
| GET | `/api/rankings/users` | Public | Get user rankings |
| GET | `/api/history/{userId}` | Required | Get reading history |
| GET | `/api/analytics/{novelId}` | Required | Get novel analytics |

## Authentication

**✅ Current Implementation**: JWT authentication is **centralized at the API Gateway level**. All JWT tokens are validated once at the gateway before routing to microservices.

### Benefits
- ✅ **Single Point of Validation**: Validate JWT token once at gateway, not in each service
- ✅ **Early Rejection**: Reject invalid/expired tokens before routing (saves resources)
- ✅ **Centralized Security Policy**: All authentication logic in one place
- ✅ **Consistent Authentication**: All services receive validated user information
- ✅ **Better Performance**: Reduced load on microservices (no JWT validation overhead)
- ✅ **Simplified Services**: Microservices only trust gateway-validated requests
- ✅ **HMAC Signature Protection**: Prevents header forgery attacks with cryptographic signatures
- ✅ **Replay Attack Prevention**: Timestamp validation prevents reuse of old signatures

### How It Works

1. **Client Request** → API Gateway with JWT token in `Authorization: Bearer <token>` header
2. **Gateway Validation** → `JwtAuthenticationGatewayFilter` validates token:
   - Checks token signature, expiration, and format
   - Extracts user information (userId, email, role, etc.)
   - Rejects invalid/expired tokens with 401 Unauthorized
3. **Header Enrichment** → Gateway adds validated user info to request headers:
   - `X-Gateway-Validated: true` - Marks request as gateway-validated
   - `X-User-Id: <userId>` - User ID from token
   - `X-User-Email: <email>` - User email from token
   - `X-User-Username: <username>` - Username from token
   - `X-User-Role: <role>` - User role from token
   - `X-User-Status: <status>` - User status from token (0=NORMAL, 1=SUSPENDED, 2=BANNED)
   - `X-Gateway-Timestamp: <timestamp>` - Request timestamp (milliseconds)
   - `X-Gateway-Signature: <hmac-signature>` - HMAC-SHA256 signature to prevent header forgery
4. **HMAC Signature** → Gateway generates HMAC signature using shared secret:
   - Signature includes: `userId|email|role|status|timestamp`
   - Algorithm: HMAC-SHA256
   - Base64-encoded signature prevents attackers from forging gateway headers
5. **Service Trust** → Microservices verify HMAC signature before trusting headers:
   - Services verify signature using shared secret
   - Check timestamp to prevent replay attacks (5-minute tolerance)
   - Extract user info from gateway headers (no JWT validation needed)
   - **Check user status** - Verify user is enabled (not suspended/banned) using `isEnabled()` check
   - Reject disabled users with **403 Forbidden** response
   - Set authentication context from headers (only if user is enabled)

### Public Endpoints

These endpoints **do NOT require authentication** (skipped by gateway filter):
- `/api/v1/auth/**` - Authentication endpoints (login, register, refresh)
- `/api/v1/public/**` - Public endpoints
- `/api/v1/health` - Health checks
- `/actuator/**` - Actuator endpoints
- `/v3/api-docs/**`, `/swagger-ui/**` - API documentation
- `/api/v1/novels` (GET) - Browse novels (public)
- `/api/v1/categories` (GET) - Browse categories (public)
- `/api/v1/comments` (GET) - Read comments (public)
- `/api/v1/reviews` (GET) - Read reviews (public)
- `/api/v1/ranking/**` (GET) - Public rankings
- And more... (see `JwtAuthenticationGatewayFilter` for complete list)

### Getting a Token

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "user@example.com",
    "password": "password123"
  }'
```

### Using the Token

```bash
curl http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

### Inter-Service Communication

When services call each other via Feign clients:
- **Preferred**: Forward gateway headers (`X-Gateway-Validated`, `X-User-Id`, etc.)
- **Fallback**: Forward JWT token in `Authorization` header (backward compatibility)
- Target service will validate JWT token if gateway headers are not present

## Health Check

```bash
curl http://localhost:8080/actuator/health
```

**Response:**
```json
{
  "status": "UP"
}
```

## Configuration

### HMAC Signature Configuration

The Gateway uses HMAC-SHA256 signatures to prevent header forgery attacks. Configure the shared secret:

```yaml
gateway:
  hmac:
    secret: ${GATEWAY_HMAC_SECRET:yushan-gateway-hmac-secret-key-for-request-signature-2024}
```

**Important**: The same secret must be configured in all microservices for signature verification to work.

**Environment Variable**:
- `GATEWAY_HMAC_SECRET`: Shared secret for HMAC signature generation/verification

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | Gateway port | `8080` |
| `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | Eureka server URL | `http://localhost:8761/eureka/` |

### application.yml

Key configurations:

```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      discovery:
        locator:
          enabled: true

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
```

## Docker

### Build Image

```bash
docker build -t yushan-api-gateway:latest .
```

### Run Container

```bash
# Connect to local services
docker run -d \
  --name yushan-api-gateway \
  -p 8080:8080 \
  -e EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://host.docker.internal:8761/eureka/ \
  yushan-api-gateway:latest
```

### Check Logs

```bash
docker logs -f yushan-api-gateway
```

### Stop and Remove

```bash
docker stop yushan-api-gateway
docker rm yushan-api-gateway
```

## Docker Compose

If running the full stack:

```yaml
services:
  api-gateway:
    build: ./yushan-api-gateway
    ports:
      - "8080:8080"
    environment:
      - EUREKA_CLIENT_SERVICEURL_DEFAULTZONE=http://eureka-server:8761/eureka/
    depends_on:
      - eureka-server
```

Run with:
```bash
docker-compose up -d api-gateway
```

## Testing

### Run Tests

```bash
./mvnw test
```

### Test Routing

```bash
# Test public endpoint
curl http://localhost:8080/api/auth/login

# Test health check
curl http://localhost:8080/actuator/health

# Test routing to User Service
curl http://localhost:8080/api/users/1 \
  -H "Authorization: Bearer YOUR_TOKEN"

# Test routing to Content Service
curl http://localhost:8080/api/novels

# Test routing to Analytics Service
curl http://localhost:8080/api/rankings/novels
```

## Monitoring

### Gateway Routes

View all configured routes:
```bash
curl http://localhost:8080/actuator/gateway/routes
```

### Eureka Dashboard

Check service registration:
```
http://localhost:8761
```

You should see `API-GATEWAY` listed among registered services.

## Troubleshooting

### Issue: Gateway can't connect to services

**Check:**
1. Eureka Server is running on port 8761
2. All microservices are registered in Eureka
3. Service names match the routes in `application.yml`

```bash
# Check Eureka for registered services
curl http://localhost:8761/eureka/apps
```

### Issue: JWT validation fails

**Check:**
1. JWT secret matches the User Service configuration
2. Token format is correct: `Bearer <token>`
3. Token hasn't expired

### Issue: CORS errors

**Solution:**
CORS is configured globally to allow all origins. If you need to restrict origins, update `CorsConfig.java`:

```java
corsConfig.setAllowedOrigins(List.of("http://localhost:3000", "https://yourdomain.com"));
```

### Issue: Port 8080 already in use

```bash
# Find process using port 8080
lsof -i :8080  # Mac/Linux
netstat -ano | findstr :8080  # Windows

# Change port in application.yml or via environment variable
SERVER_PORT=8090 ./mvnw spring-boot:run
```

## Development

### Project Structure

```
yushan-api-gateway/
├── src/
│   ├── main/
│   │   ├── java/com/yushan/gateway/
│   │   │   ├── ApiGatewayApplication.java
│   │   │   ├── config/
│   │   │   │   ├── CorsConfig.java
│   │   │   │   └── GatewayConfig.java
│   │   │   └── filter/
│   │   │       ├── AuthenticationFilter.java
│   │   │       └── LoggingFilter.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-docker.yml
│   └── test/
├── Dockerfile
├── pom.xml
└── README.md
```

### Adding New Routes

To add routes for a new service, update `application.yml`:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: new-service
          uri: lb://new-service
          predicates:
            - Path=/api/newservice/**
          filters:
            - RewritePath=/api/newservice/(?<segment>.*), /${segment}
```

### Adding Authentication Exceptions

To make endpoints public, update `AuthenticationFilter.java`:

```java
private static final List<String> PUBLIC_PATHS = List.of(
    "/api/auth/login",
    "/api/auth/register",
    "/api/your-new-public-endpoint"
);
```

### Gateway-Level JWT Validation (Phase 3 Recommended)

For Phase 3, implement centralized JWT validation at the gateway:

```java
@Component
public class JwtAuthenticationGatewayFilter implements GatewayFilter {
    
    @Autowired
    private JwtUtil jwtUtil;
    
    private static final List<String> PUBLIC_PATHS = List.of(
        "/api/v1/auth/login",
        "/api/v1/auth/register",
        "/api/v1/auth/refresh",
        "/api/v1/novels",  // Public browsing
        "/api/v1/categories"
    );
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        
        // Skip validation for public endpoints
        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            return chain.filter(exchange);
        }
        
        // Extract token from Authorization header
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorized(exchange);
        }
        
        String token = authHeader.substring(7);
        
        // Validate token
        if (!jwtUtil.validateToken(token)) {
            return unauthorized(exchange);
        }
        
        // Extract user info and add to request headers
        String userId = jwtUtil.extractUserId(token);
        String email = jwtUtil.extractEmail(token);
        
        // Forward user info to downstream services
        ServerHttpRequest modifiedRequest = request.mutate()
            .header("X-User-Id", userId)
            .header("X-User-Email", email)
            .build();
        
        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }
    
    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return response.setComplete();
    }
}
```

**Configuration**:
```yaml
spring:
  cloud:
    gateway:
      default-filters:
        - name: JwtAuthentication
          args:
            jwtSecret: ${JWT_SECRET}
```

**Benefits**:
- ✅ Single point of authentication
- ✅ Microservices can trust gateway-validated requests
- ✅ Reduced authentication overhead in services
- ✅ Consistent security policy

## Performance

### Load Balancing

The gateway automatically load balances requests across multiple instances of the same service using Eureka's service registry.

### Timeouts

Default timeout is 30 seconds. To adjust:

```yaml
spring:
  cloud:
    gateway:
      httpclient:
        connect-timeout: 5000
        response-timeout: 30s
```

## Security

### Best Practices

1. **Always use HTTPS in production**
2. **Keep JWT secret secure** - use environment variables
3. **Rotate JWT secrets regularly**
4. **Implement rate limiting** (consider Spring Cloud Gateway rate limiter)
5. **Monitor failed authentication attempts**

## Contributing

1. Create a feature branch
2. Make your changes
3. Run tests: `./mvnw test`
4. Submit a pull request

## License

This project is part of the Yushan Novel Platform.

## Support

For issues or questions:
- Check the troubleshooting section
- Review service logs: `docker logs yushan-api-gateway`
- Verify Eureka registration: `http://localhost:8761`

## Version

Current version: 1.0.0

## Dependencies

- Spring Boot 3.4.10
- Spring Cloud Gateway 2024.0.2
- Spring Cloud Netflix Eureka Client
- JJWT 0.12.6
- Java 21

## Security

### Vulnerability Fixes

- ✅ **CVE-2025-48924 Fixed**: Excluded vulnerable `commons-lang:commons-lang@2.6` from `spring-cloud-starter-gateway` and `spring-cloud-starter-netflix-eureka-client` dependencies
- ✅ **Security Scanning**: CI/CD pipeline includes OWASP Dependency Check and Snyk vulnerability scanning
- ✅ **HMAC Signature Protection**: Prevents header forgery attacks with cryptographic signatures

## CI/CD Pipeline

The API Gateway includes a comprehensive CI/CD pipeline with:

- ✅ **Unit Tests**: JUnit 5 tests for JWT utilities, HMAC utilities, and gateway filters
- ✅ **Code Quality**: SpotBugs static analysis, Checkstyle code style checks
- ✅ **Code Coverage**: JaCoCo coverage reports
- ✅ **Security Scanning**: OWASP Dependency Check and Snyk vulnerability scanning
- ✅ **Container Scanning**: Trivy container vulnerability scanning
- ✅ **Quality Gates**: SonarCloud analysis and quality gates
- ✅ **Docker Build**: Automated Docker image building and pushing to GitHub Container Registry

---

## Overview

This API Gateway is part of **Phase 3: Kubernetes & AWS Deployment** of the Yushan Platform. It serves as the single entry point for all microservices, routing requests to the appropriate backend services through service discovery with Eureka.

**Deployment**: All backend services including this API Gateway are deployed on **Digital Ocean** using Terraform (Infrastructure as Code).

**Status**: ✅ Production Ready | 🔄 Phase 3 Development

---

## Links

- **Service Registry**: [yushan-microservices-service-registry](https://github.com/phutruonnttn/yushan-microservices-service-registry)
- **Config Server**: [yushan-microservices-config-server](https://github.com/phutruonnttn/yushan-microservices-config-server)
- **User Service**: [yushan-microservices-user-service](https://github.com/phutruonnttn/yushan-microservices-user-service)
- **Content Service**: [yushan-microservices-content-service](https://github.com/phutruonnttn/yushan-microservices-content-service)
- **Engagement Service**: [yushan-microservices-engagement-service](https://github.com/phutruonnttn/yushan-microservices-engagement-service)
- **Gamification Service**: [yushan-microservices-gamification-service](https://github.com/phutruonnttn/yushan-microservices-gamification-service)
- **Analytics Service**: [yushan-microservices-analytics-service](https://github.com/phutruonnttn/yushan-microservices-analytics-service)
- **Platform Documentation**: [yushan-platform-docs](https://github.com/phutruonnttn/yushan-platform-docs) - Complete documentation for all phases
- **Phase 2 Architecture**: See [Phase 2 Microservices Architecture](https://github.com/phutruonnttn/yushan-platform-docs/blob/main/docs/phase2-microservices/PHASE2_MICROSERVICES_ARCHITECTURE.md)
