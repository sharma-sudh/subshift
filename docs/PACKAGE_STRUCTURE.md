# SubShift Backend — Package Structure

Base package: `com.subshift.backend`.

| Package | Contents | Tables |
|---|---|---|
| `users` | `Role`, `User` + repositories | `roles`, `users` |
| `services` | `Category`, `ServiceEntity`* + repositories, `ServiceController`, `dto/` | `categories`, `services` |
| `subscriptions` | `Subscription`, `GmailConnection` + repositories, `SubscriptionController`, `dto/` | `subscriptions`, `gmail_connections` |
| `pricelog` | `PriceLog` + repository | `price_log` |
| `alerts` | `Alert` + repository | `alerts` |
| `notifications` | `Notification` + repository | `notifications` |
| `ml` | `ClassifierTrainingEmail` + repository | `classifier_training_emails` |
| `auth` | `AuthController`, `JwtService`, `JwtAuthFilter`, `CustomUserDetailsService`, `dto/` | (no table — auth logic only) |
| `config` | `SecurityConfig` | — |
| `common` | Cross-cutting stuff with no clear home yet (currently just `HealthController`) | — |

\* Named `ServiceEntity`, not `Service` — avoids clashing with Spring's
`@Service` stereotype annotation in files that import both.