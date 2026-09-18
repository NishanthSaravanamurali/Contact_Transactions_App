# Live API Gateway Demonstration

For a complete GUI-driven test of every implemented endpoint and important failure
case, use the [Insomnia live-test plan](insomnia-live-test-plan.md).

## 1. Start the applications

Start the applications in separate IntelliJ run configurations, in this order:

1. `DiscoveryServerApplication`
2. `UserServiceApplication`
3. `ApiGatewayApplication`

The Gateway's `JWT_PUBLIC_KEY` must exactly match the User Service public key.

Open `http://localhost:8761` and confirm that Eureka shows:

```text
USER-SERVICE
API-GATEWAY
```

## 2. Check health

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health
Invoke-RestMethod http://localhost:8080/actuator/health
```

Both responses should contain `status` equal to `UP`.

## 3. Register directly with User Service

Use a unique email and mobile number:

```powershell
$demoSuffix = Get-Date -Format 'HHmmss'

$directRegistration = @{
    name = 'Direct Demo User'
    email = "direct$demoSuffix@example.com"
    password = 'Strong@Password123'
    mobileNo = '9876543210'
    dateOfBirth = '2000-06-15'
} | ConvertTo-Json

Invoke-RestMethod `
    -Method Post `
    -Uri 'http://localhost:8081/api/v1/auth/register' `
    -ContentType 'application/json' `
    -Body $directRegistration
```

Expected result: `201 Created` and an `ACTIVE` user response.

## 4. Register through the Gateway

```powershell
$gatewayEmail = "gateway$demoSuffix@example.com"

$gatewayRegistration = @{
    name = 'Gateway Demo User'
    email = $gatewayEmail
    password = 'Strong@Password123'
    mobileNo = '9123456789'
    dateOfBirth = '2000-06-15'
} | ConvertTo-Json

Invoke-RestMethod `
    -Method Post `
    -Uri 'http://localhost:8080/api/v1/auth/register' `
    -ContentType 'application/json' `
    -Body $gatewayRegistration
```

Expected result: `201 Created`. This proves Gateway routing through Eureka.

## 5. Log in through the Gateway

```powershell
$loginBody = @{
    email = $gatewayEmail
    password = 'Strong@Password123'
} | ConvertTo-Json

$loginResponse = Invoke-RestMethod `
    -Method Post `
    -Uri 'http://localhost:8080/api/v1/auth/login' `
    -ContentType 'application/json' `
    -Body $loginBody

$loginResponse
$accessToken = $loginResponse.accessToken
```

Expected result: `tokenType` is `Bearer`, `expiresIn` is `1800`, and
`accessToken` contains the signed JWT.

Do not print tokens in real application logs. Displaying it in this local terminal
variable is only for the manual demonstration.

## 6. Confirm an unauthenticated profile request is rejected

```powershell
try {
    Invoke-WebRequest 'http://localhost:8080/api/v1/users/me'
} catch {
    [int]$_.Exception.Response.StatusCode
}
```

Expected result: `401`.

## 7. Read the authenticated profile through the Gateway

```powershell
$authHeaders = @{
    Authorization = "Bearer $accessToken"
}

Invoke-RestMethod `
    -Method Get `
    -Uri 'http://localhost:8080/api/v1/users/me' `
    -Headers $authHeaders
```

Expected result: `200 OK` and the Gateway demo user's safe profile.

## 8. Confirm internal APIs are not public

```powershell
try {
    Invoke-WebRequest 'http://localhost:8080/internal/v1/users/1/status'
} catch {
    [int]$_.Exception.Response.StatusCode
}
```

Expected result: access is rejected. No `/internal/**` Gateway route exists.

## 9. Call the internal API directly

Run this only from a trusted local service/test environment:

```powershell
$internalHeaders = @{
    'X-Internal-Service-Token' = $env:INTERNAL_SERVICE_TOKEN
}

Invoke-RestMethod `
    -Method Get `
    -Uri 'http://localhost:8081/internal/v1/users/1/status' `
    -Headers $internalHeaders
```

Expected result: `200 OK` with only `userId` and `status`.

## 10. Logout

```powershell
Invoke-WebRequest `
    -Method Post `
    -Uri 'http://localhost:8080/api/v1/auth/logout' `
    -Headers $authHeaders
```

Expected result: `204 No Content`. Remove `$accessToken` and `$authHeaders` from
the client session afterward.
