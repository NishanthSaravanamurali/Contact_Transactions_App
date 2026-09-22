# IntelliJ IDEA and Local Setup

This guide prepares a Windows development environment for `CONTACT-SERVICE`.
The service uses Java 24, Maven, Spring Boot, Oracle Database, and Eureka.

## 1. Verify Java 24

Open PowerShell and run:

```powershell
java -version
javac -version
```

Both commands must report Java 24. If they report another version, configure
`JAVA_HOME` for the current PowerShell session:

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-24"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

Verify again:

```powershell
java -version
javac -version
```

`JAVA_HOME` points to the JDK installation. The JDK contains the Java compiler,
runtime, and developer tools. The JVM is the runtime inside the JDK that
executes compiled Java bytecode.

## 2. Open the project

In IntelliJ IDEA:

1. Select `File → Open`.
2. Choose the folder containing `pom.xml`.
3. Select **Open as Project** when prompted.
4. Allow IntelliJ to import the Maven project.

Do not open only the `src` folder. IntelliJ must open the directory containing
`pom.xml`, `.mvn`, `mvnw`, and `mvnw.cmd`.

## 3. Configure the IntelliJ JDK

Set all Java selectors to JDK 24:

1. `File → Project Structure → Project`
   - Project SDK: `JDK 24`
   - Language level: `24`
2. `Settings → Build, Execution, Deployment → Build Tools → Maven → Runner`
   - JRE: `JDK 24`
3. `Settings → Build, Execution, Deployment → Build Tools → Maven → Importing`
   - JDK for importer: `JDK 24`

If JDK 24 is not listed:

1. Select **Add SDK → JDK**.
2. Choose the JDK directory, normally:

```text
C:\Program Files\Java\jdk-24
```

The Project SDK controls IntelliJ compilation and code analysis. The Maven
Runner JDK controls the JVM used when IntelliJ launches Maven. Setting only one
of them can cause confusing version differences.

## 4. Configure Maven

Open:

```text
Settings → Build, Execution, Deployment → Build Tools → Maven
```

Confirm:

- Maven home path uses the wrapper or Maven 3.9.16.
- User settings file is the intended Maven settings file.
- Local repository points to the normal user repository.
- **Work offline** is disabled.

The preferred project command is:

```powershell
.\mvnw.cmd test
```

The Windows wrapper's generated PowerShell symlink check was repaired and the
wrapper has been verified with Maven 3.9.16 and Java 24.0.2. A compatible global
Maven installation is optional.

Maven reads `pom.xml`, resolves dependencies, compiles the project, runs tests,
and packages the application. Spring Boot is the application framework managed
by those Maven dependencies; Maven and Spring Boot are not the same tool.

## 5. Confirm the Oracle service

The verified local JDBC URL is:

```text
jdbc:oracle:thin:@//localhost:1521/FREE
```

The current training setup keeps the application tables in the privileged
`SYSTEM` schema in the `FREE` root database. This is acceptable for this demo
only and is not a recommended production database layout.

In SQL Developer, connect as `SYSTEM` using:

```text
Hostname: localhost
Port: 1521
Service name: FREE
Username: SYSTEM
Password: your local SYSTEM password
```

Test the connection, then run:

```sql
SELECT SYS_CONTEXT('USERENV', 'CON_NAME') AS container_name,
       SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') AS current_schema
FROM dual;
```

Expected values:

```text
CONTAINER_NAME: CDB$ROOT
CURRENT_SCHEMA: SYSTEM
```

Confirm the database objects:

```sql
SELECT table_name
FROM user_tables
WHERE table_name = 'CONTACT';

SELECT index_name
FROM user_indexes
WHERE table_name = 'CONTACT'
ORDER BY index_name;

SELECT constraint_name, constraint_type, status
FROM user_constraints
WHERE table_name = 'CONTACT'
ORDER BY constraint_name;

SELECT trigger_name, status
FROM user_triggers
WHERE table_name = 'CONTACT';
```

The expected custom objects are:

- Table: `CONTACT`
- Primary key: `PK_CONTACT`
- Check constraint: `CK_CONTACT_NOT_SELF`
- Indexes: `IX_CONTACT_OWNER`, `IX_CONTACT_LINKED`
- Trigger: `TRG_CONTACT_BU`, with status `ENABLED`

## 6. Configure environment variables in IntelliJ

The project uses one Spring configuration file:

```text
src/main/resources/application.properties
```

`.env.example` documents variable names but Spring Boot does not automatically
load `.env` files.

Create an IntelliJ Spring Boot run configuration:

1. Open `ContactServiceApplication.java`.
2. Select the run icon beside `main`.
3. Choose **Modify Run Configuration**.
4. Set the name to `CONTACT-SERVICE`.
5. Set the JRE to JDK 24.
6. Add the following environment variables:

```text
DB_URL=jdbc:oracle:thin:@//localhost:1521/FREE
DB_USERNAME=SYSTEM
DB_PASSWORD=<your local password>
EUREKA_URL=http://localhost:8761/eureka/
USER_SERVICE_INTERNAL_TOKEN=<shared internal token>
INTERNAL_SERVICE_TOKEN=<shared token accepted from Transaction Service>
```

Do not add quotes around values in IntelliJ's environment-variable editor
unless the value itself requires them. Do not commit real values.

JWT is currently deferred. Do not configure or connect `JWT_PUBLIC_KEY` at this
stage.

## 7. Reload and compile

After changing `pom.xml` or importing the project:

1. Open the Maven tool window.
2. Select **Reload All Maven Projects**.
3. Wait for indexing and dependency resolution to finish.
4. Confirm there are no Java SDK errors in the editor.

Compile from PowerShell:

```powershell
mvn -DskipTests compile
```

Expected result:

```text
BUILD SUCCESS
```

## 8. Run tests

Unit and MVC tests can run without a live Eureka Server. The real Oracle tests
require `DB_USERNAME` and `DB_PASSWORD`; when those variables are absent, the
Oracle test class is skipped intentionally.

To run every test, supply the database and internal-token environment variables
to the IntelliJ Maven run configuration or current PowerShell session, then run:

```powershell
mvn test
```

After changing the training database location to `FREE/SYSTEM`, the latest
credential-free verification produced:

```text
Tests run: 51, Failures: 0, Errors: 0, Skipped: 4
BUILD SUCCESS
```

The four skipped tests require the local SYSTEM database credentials. Run the
suite again with those environment variables to verify `SYSTEM.CONTACT`.

The self-link constraint test deliberately causes Oracle error `ORA-02290`.
Seeing that warning during the test is expected; the test passes when Oracle
rejects the invalid row.

## 9. Run the application in IntelliJ

Before starting the service:

1. Ensure Oracle and its listener are running.
2. Confirm `SYSTEM.CONTACT` exists through service `FREE`.
3. Start Eureka Server if live discovery registration is being tested.
4. Supply all required environment variables.

Run `ContactServiceApplication.main()` using the `CONTACT-SERVICE`
configuration.

Successful startup should include messages indicating:

- Spring Boot started with Java 24.
- HikariCP connected to Oracle.
- Hibernate validated the schema.
- The embedded server started on port `8082`.
- Eureka registration started when Eureka is available.

If Eureka is unavailable, the client can log registration/connection retries.
That is separate from an Oracle authentication or schema-validation failure.

## 10. Current runtime limitations

At this stage:

- The business service and persistence layers are implemented.
- The public Contact REST controller is intentionally deferred.
- JWT authentication is intentionally not connected.
- Spring Security may print a generated development password.
- Gateway routing is not yet configured in this repository.

Therefore, successful application startup verifies configuration and wiring,
but Contact CRUD URLs are not yet available for manual Postman calls.

## Common errors

### Maven uses the wrong Java version

Symptom:

```text
release version 24 not supported
```

Fix `JAVA_HOME`, the IntelliJ Project SDK, Maven Runner JDK, and Maven Importer
JDK so all four use JDK 24.

### Oracle listener or service is unavailable

Confirm the listener is running and the URL uses service name `FREE`:

```text
jdbc:oracle:thin:@//localhost:1521/FREE
```

### Invalid username or password

Test the same credentials in SQL Developer against `FREE`. Do not solve an
authentication problem by hardcoding credentials in `application.properties`.

### Hibernate schema validation fails

The application uses `ddl-auto=validate`; it will not repair the schema. Compare
the table, types, constraints, and trigger with the authoritative DDL in the
root README.

### User Service token property is missing

`UserServiceClient` requires `USER_SERVICE_INTERNAL_TOKEN` during application
startup. Supply it through the run configuration. Never print or log its value.

### Eureka is unavailable

Check that Eureka Server is running at `EUREKA_URL`. Unit tests disable Eureka
so they do not depend on an external registry.
