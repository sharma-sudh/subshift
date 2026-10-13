# Manual Entry API: Test Examples

Endpoint: `POST http://localhost:8080/api/subscriptions/manual`

Adds a subscription for the logged-in user (if it doesn't exist yet) and records a price. A new `price_log` row is inserted only when the price differs from the latest recorded price.

## Before you start

1. The backend is running on `localhost:8080`.
2. The database has at least one row in `services` (the examples use `serviceId` 1).
3. You have a registered user. Log in to get a JWT:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "password": "your-password"}'
```

Copy the token from the response and use it wherever you see `<YOUR_JWT_TOKEN>` below.

> **Windows PowerShell users:** `curl` is an alias for a different command, and quoting JSON for `curl.exe` does not work reliably in Windows PowerShell 5.1. Either run the commands below in **Git Bash**, or use `Invoke-RestMethod`, for example:
>
> ```powershell
> Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/subscriptions/manual `
>   -Headers @{ Authorization = "Bearer <YOUR_JWT_TOKEN>" } `
>   -ContentType "application/json" `
>   -Body '{"serviceId": 1, "price": 199, "dateRecorded": "2026-01-15"}'
> ```

---

## Success cases

### 1. First entry for a service (creates the subscription)

Expected: **HTTP 201**. A new subscription and a new `price_log` row (source `MANUAL`) are created.

```bash
curl -i -X POST http://localhost:8080/api/subscriptions/manual \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <YOUR_JWT_TOKEN>" \
  -d '{"serviceId": 1, "price": 199, "dateRecorded": "2026-01-15"}'
```

Example response body:

```json
{
  "subscriptionId": 1,
  "serviceId": 1,
  "serviceName": "Netflix",
  "category": "entertainment",
  "status": "ACTIVE",
  "latestPrice": 199,
  "lastCheckedDate": "2026-10-13"
}
```

### 2. Same service, same price (no new price log)

Expected: **HTTP 200**. No new `price_log` row, and `lastCheckedDate` is updated.

```bash
curl -i -X POST http://localhost:8080/api/subscriptions/manual \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <YOUR_JWT_TOKEN>" \
  -d '{"serviceId": 1, "price": 199, "dateRecorded": "2026-02-15"}'
```

### 3. Same service, higher price (price hike)

Expected: **HTTP 200**. A new `price_log` row is inserted, and the database trigger creates an alert (old 199, new 249).

```bash
curl -i -X POST http://localhost:8080/api/subscriptions/manual \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <YOUR_JWT_TOKEN>" \
  -d '{"serviceId": 1, "price": 249, "dateRecorded": "2026-03-15"}'
```

---

## Error cases

### 4. Missing price

Expected: **HTTP 400** (`price is required`).

```bash
curl -i -X POST http://localhost:8080/api/subscriptions/manual \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <YOUR_JWT_TOKEN>" \
  -d '{"serviceId": 1, "dateRecorded": "2026-01-15"}'
```

### 5. Negative price

Expected: **HTTP 400** (`price must be positive`).

```bash
curl -i -X POST http://localhost:8080/api/subscriptions/manual \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <YOUR_JWT_TOKEN>" \
  -d '{"serviceId": 1, "price": -50, "dateRecorded": "2026-01-15"}'
```

### 6. Future date

Expected: **HTTP 400** (`dateRecorded cannot be in the future`).

```bash
curl -i -X POST http://localhost:8080/api/subscriptions/manual \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <YOUR_JWT_TOKEN>" \
  -d '{"serviceId": 1, "price": 199, "dateRecorded": "2099-01-01"}'
```

### 7. Unknown serviceId

Expected: **HTTP 404** (`Service not found`).

```bash
curl -i -X POST http://localhost:8080/api/subscriptions/manual \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <YOUR_JWT_TOKEN>" \
  -d '{"serviceId": 999999, "price": 199, "dateRecorded": "2026-01-15"}'
```

### 8. Missing or invalid token (extra)

Expected: **HTTP 401 or 403**, depending on the security configuration.

```bash
curl -i -X POST http://localhost:8080/api/subscriptions/manual \
  -H "Content-Type: application/json" \
  -d '{"serviceId": 1, "price": 199, "dateRecorded": "2026-01-15"}'
```

---

## Verify in the database

After running cases 1 to 3, check the data with `psql`:

```sql
SELECT * FROM subscriptions;
SELECT * FROM price_log ORDER BY id;
SELECT * FROM alerts;
```

Expected after cases 1 to 3: one subscription, two `price_log` rows (199 and 249, both `MANUAL`), and one alert (old 199, new 249).

> The alert row only appears once the price-hike database trigger exists (planned for Review 2). Until then, `alerts` stays empty.
