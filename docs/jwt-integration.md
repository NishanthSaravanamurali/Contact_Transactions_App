# JWT Integration for Contact and Transaction Services

This document describes the JWT contract currently implemented by User Service and
API Gateway. Contact Service and Transaction/Money Service must follow the same
contract when protecting their public APIs.

## Security model

```text
Client
  |  email + password
  v
User Service
  |  signs JWT with RSA private key
  v
Client
  |  Authorization: Bearer <JWT>
  v
API Gateway
  |  validates JWT with RSA public key
  |  forwards the unchanged Authorization header
  v
Contact Service or Transaction/Money Service
  |  independently validates JWT with the same RSA public key
  |  reads the authenticated user ID from sub
  v
Business operation
```

The double validation is intentional. Gateway validation blocks invalid public
traffic early, while service validation protects the service if its port is called
directly or traffic reaches it through another network path.

## Key ownership

| Application | Private key | Public key |
|---|---:|---:|
| User Service | Yes: signs tokens | Yes: validates its protected APIs |
| API Gateway | Never | Yes |
| Contact Service | Never | Yes |
| Transaction/Money Service | Never | Yes |

Only User Service receives `JWT_PRIVATE_KEY`. All four applications receive the
matching `JWT_PUBLIC_KEY` where required. Keys must be supplied through environment
variables or a secret manager and must not be committed to Git.

Sharing the public key is safe: it can verify signatures but cannot create a valid
signature. Sharing the private key would allow another application to impersonate
any user and is therefore prohibited.

## Token creation

User Service creates a token only after:

1. Normalizing the supplied email.
2. Loading the user from Oracle.
3. Requiring status `ACTIVE`.
4. Comparing the submitted password with the stored Argon2id hash.
5. Signing a short-lived JWT using RS256 and the private RSA key.

The default lifetime is 30 minutes and is configured with:

```properties
security.jwt.access-token-expiry=${JWT_EXPIRY:PT30M}
```

## JWT structure

The protected header uses RS256:

```json
{
  "alg": "RS256"
}
```

The payload follows this contract:

```json
{
  "sub": "42",
  "email": "alex@example.com",
  "iat": 1789450000,
  "exp": 1789451800
}
```

| Claim | Meaning | Authorization use |
|---|---|---|
| `sub` | User Service numeric user ID encoded as a string | Authoritative authenticated user ID |
| `email` | Normalized email at token-creation time | Informational only; do not use as ownership authority |
| `iat` | Token issue time | Token metadata |
| `exp` | Token expiry time | Automatically enforced by Spring Security |

There are currently no role, scope, issuer, or audience claims. Downstream services
must not invent role checks that the token does not support.

## HTTP contract

The client sends:

```http
Authorization: Bearer <access-token>
```

Missing, malformed, expired, modified, or incorrectly signed tokens must produce
`401 Unauthorized`. A valid token that lacks permission for a particular operation
must produce `403 Forbidden`.

The raw JWT must never be logged, included in an event, stored in an application
table, or returned by any endpoint other than the login response.

## Required Maven dependencies

For a Spring Boot 4.1.1 MVC service, add these dependencies without explicit
versions. Spring Boot dependency management supplies compatible versions.

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security-oauth2-resource-server</artifactId>
</dependency>
```

Do not add a second JWT library. Spring Security Resource Server and JOSE support
perform signature and timestamp validation.

## Downstream configuration

Add this to Contact Service and Transaction/Money Service
`application.properties`:

```properties
security.jwt.public-key=${JWT_PUBLIC_KEY:}
```

Add the same public key to each service's IntelliJ run configuration:

```text
Run -> Edit Configurations -> service application -> Environment variables
JWT_PUBLIC_KEY=<matching X.509 RSA public key>
```

The service must not define or request `JWT_PRIVATE_KEY`.

## Public-key properties

Create a configuration-properties class in the downstream service. Replace the
package with that service's base package.

```java
package com.contacttx.contactservice.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public class JwtValidationProperties {

    private String publicKey;

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }
}
```

## Public-key parser

The configured public key is X.509 PEM. This parser accepts actual line breaks or
literal `\n` characters from an IntelliJ environment-variable value.

```java
package com.contacttx.contactservice.security;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public final class RsaPublicKeyParser {

    private static final String BEGIN = "-----BEGIN PUBLIC KEY-----";
    private static final String END = "-----END PUBLIC KEY-----";

    private RsaPublicKeyParser() {
    }

    public static RSAPublicKey parse(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("JWT public key is not configured");
        }

        String normalized = pem.replace("\\n", "\n").trim();
        if (!normalized.contains(BEGIN) || !normalized.contains(END)) {
            throw new IllegalStateException(
                    "JWT public key does not contain the expected PEM markers");
        }

        String encoded = normalized
                .replace(BEGIN, "")
                .replace(END, "")
                .replaceAll("\\s", "");

        try {
            byte[] keyBytes = Base64.getDecoder().decode(encoded);
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(keyBytes));
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "JWT public key must be a valid X.509 RSA PEM value", exception);
        }
    }
}
```

Transaction/Money Service should use its own package, for example
`com.contacttx.moneyservice.security`.

## JWT decoder

Register a Spring Security decoder using only the public key:

```java
package com.contacttx.contactservice.config;

import com.contacttx.contactservice.security.JwtValidationProperties;
import com.contacttx.contactservice.security.RsaPublicKeyParser;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtValidationProperties.class)
public class JwtConfig {

    @Bean
    public JwtDecoder jwtDecoder(JwtValidationProperties properties) {
        return NimbusJwtDecoder
                .withPublicKey(RsaPublicKeyParser.parse(properties.getPublicKey()))
                .build();
    }
}
```

`NimbusJwtDecoder` verifies the RSA signature and rejects invalid token timestamps,
including expired tokens.

## Security filter chain

Each downstream service requires a stateless servlet security chain. This example
allows health checks, protects the service's public API, and denies unknown paths.

```java
package com.contacttx.contactservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtDecoder jwtDecoder) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/actuator/health")
                        .permitAll()
                        .requestMatchers("/api/v1/contacts/**")
                        .authenticated()
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(jwtDecoder)));

        return http.build();
    }
}
```

Transaction/Money Service should replace `/api/v1/contacts/**` with its own public
path, such as `/api/v1/money/**`. If a service has internal endpoints, protect them
with the separately configured internal-service credential rather than making them
public JWT endpoints.

## Extracting the authenticated user ID

Create a resolver that treats `sub` as the only user-identity authority:

```java
package com.contacttx.contactservice.security;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserIdResolver {

    public Long resolve(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new IllegalArgumentException("Authenticated user ID is missing");
        }

        try {
            return Long.valueOf(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Authenticated user ID is invalid");
        }
    }
}
```

Use it in controllers:

```java
@PostMapping("/api/v1/contacts")
public ResponseEntity<ContactResponse> createContact(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreateContactRequest request) {
    Long ownerUserId = authenticatedUserIdResolver.resolve(jwt);
    return ResponseEntity.status(HttpStatus.CREATED)
            .body(contactService.create(ownerUserId, request));
}
```

For Transaction/Money Service:

```java
@PostMapping("/api/v1/wallets/transfer")
public ResponseEntity<TransferResponse> transfer(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody TransferRequest request) {
    Long payerUserId = authenticatedUserIdResolver.resolve(jwt);
    return ResponseEntity.ok(
            transferService.transfer(payerUserId, request));
}
```

The request DTO must not allow the client to choose `ownerUserId`, `payerUserId`,
`wallet.userId`, or `account.userId`. Those values come from the validated JWT.

## JWT validation versus user status validation

A valid JWT proves that User Service signed the token and that it has not expired.
It does not prove that the account is still `ACTIVE` after the token was issued.

Before sensitive actions, Contact and Transaction/Money Services use the internal
status contract:

```http
GET http://USER-SERVICE/internal/v1/users/{sub}/status
X-Internal-Service-Token: <service-token>
```

Proceed only when the response status is `ACTIVE`. The internal service token and
the user's bearer JWT solve different problems and are not interchangeable.

## Gateway integration

When Contact and Transaction/Money Services are added, Gateway needs explicit
Eureka routes for their public paths. Do not add `/internal/**` routes.

Example route destinations:

```text
/api/v1/contacts/** -> lb://CONTACT-SERVICE
/api/v1/money/**    -> lb://MONEY-SERVICE
```

Gateway must keep forwarding the original `Authorization` header. Spring Cloud
Gateway does this by default unless a filter explicitly removes or replaces it.

## Required verification

For each downstream service, verify:

1. A valid User Service token reaches the authenticated endpoint.
2. A missing token returns `401`.
3. An expired token returns `401`.
4. A token with a changed payload returns `401`.
5. A token signed by a different private key returns `401`.
6. The service uses `sub`, not a request-body user ID, for ownership.
7. A valid token belonging to an inactive user is rejected for sensitive work
   after the internal status check.

## Current V1 limitations

- Logout does not revoke an existing token; it remains valid until `exp`.
- There is no refresh token or token denylist.
- Tokens currently have no `iss` or `aud` claim. Do not configure issuer or
  audience validation until User Service adds matching claims.
- There is no `kid` header or JWKS endpoint. RSA key rotation currently requires a
  coordinated public-key update and restart of Gateway and downstream services.
- Tokens contain no roles or scopes. Authorization is based on authenticated
  ownership and service business rules.

## Framework references

- [Spring Security servlet JWT resource-server documentation](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Spring Security `NimbusJwtDecoder` API](https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/oauth2/jwt/NimbusJwtDecoder.html)
- [Spring Boot OAuth2 resource-server documentation](https://docs.spring.io/spring-boot/reference/security/oauth2.html)
