# IntelliJ and Environment Setup

## Java and Maven

Verify Java 24 in PowerShell:

```powershell
java -version
javac -version
.\mvnw.cmd -version
```

In IntelliJ IDEA configure:

```text
File -> Project Structure -> Project SDK -> JDK 24
File -> Project Structure -> Language Level -> 24
Settings -> Build Tools -> Maven -> Runner -> JDK 24
Settings -> Build Tools -> Maven -> Importing -> JDK 24
```

Ensure Maven offline mode is disabled, then reload the root `pom.xml` as a Maven
project.

## Environment variables

IntelliJ does not automatically load a `.env` file. Configure variables through:

```text
Run -> Edit Configurations -> select the application -> Environment variables
```

### User Service

Set these on the `UserServiceApplication` run configuration:

| Variable | Example or meaning |
|---|---|
| `DB_URL` | `jdbc:oracle:thin:@//localhost:1521/FREE` |
| `DB_USERNAME` | `SYSTEM` for the current learning setup |
| `DB_PASSWORD` | Your local Oracle password; never commit it |
| `JWT_PRIVATE_KEY` | PKCS#8 RSA private key used to sign tokens |
| `JWT_PUBLIC_KEY` | Matching X.509 RSA public key used to validate tokens |
| `JWT_EXPIRY` | Optional ISO-8601 duration; default is `PT30M` |
| `INTERNAL_SERVICE_TOKEN` | Shared random secret for internal service calls |
| `EUREKA_URL` | Optional; default is `http://localhost:8761/eureka/` |

For multiline RSA values, IntelliJ may store actual line breaks or literal `\n`
sequences. The application accepts both forms. Do not put either key in
`application.properties` or Git.

### API Gateway

Set these on the `ApiGatewayApplication` run configuration:

| Variable | Example or meaning |
|---|---|
| `JWT_PUBLIC_KEY` | The same public key configured on User Service |
| `EUREKA_URL` | Optional; default is `http://localhost:8761/eureka/` |
| `CORS_ALLOWED_ORIGINS` | Comma-separated browser origins, such as `http://localhost:3000` |

The Gateway must not receive the JWT private key or database credentials.

### Discovery Server

The local `DiscoveryServerApplication` needs no secret environment variables.

## Internal service token

Generate a development token in PowerShell:

```powershell
$internalTokenBytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($internalTokenBytes)
[Convert]::ToBase64String($internalTokenBytes)
```

Copy the resulting value into `INTERNAL_SERVICE_TOKEN` for User Service. Contact
Service and Money Service must send that same value in the
`X-Internal-Service-Token` request header. Never send it to a browser or route it
through the public Gateway.

## Oracle prerequisite

This version does not run Flyway. The `APP_USER` table, constraints, indexes, and
update trigger must already exist in the Oracle account configured by
`DB_USERNAME`. Hibernate uses `ddl-auto=validate`; it checks mappings but does not
create or alter the table.

The implemented mapping expects:

```text
USER_ID       NUMBER identity primary key
NAME          VARCHAR2(60) not null
PASSWORD_HASH VARCHAR2(255) not null
EMAIL         VARCHAR2(254) not null unique
MOBILE_NO     NUMBER(10) not null
DOB           DATE not null
STATUS        VARCHAR2(20) in ACTIVE, INACTIVE, DELETED
CREATED_AT    TIMESTAMP not null
UPDATED_AT    TIMESTAMP not null
```

The existing update trigger must refresh `UPDATED_AT`; the service uses that
timestamp to detect concurrent changes.

## Starting the platform

Start `DiscoveryServerApplication`, followed by `UserServiceApplication`, then
`ApiGatewayApplication`. Confirm that Eureka lists both client applications before
running the [live Gateway demonstration](live-gateway-demo.md).
