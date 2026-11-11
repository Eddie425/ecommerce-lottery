# Independent Code Review Guide

This repository is intended to be reviewable by independent coding assistants or human reviewers. The guide provides reproducible checks and separates verified sample behavior from production assumptions.

## Reproduce the checks

Requirements: Java 21, Maven 3.9+, and Docker for the PostgreSQL Testcontainers suite.

```shell
mvn --batch-mode verify
docker compose config
```

The GitHub Actions workflow runs `mvn --batch-mode verify` on Java 21. Testcontainers tests are skipped when Docker is unavailable; a successful build without Docker does **not** verify the PostgreSQL integration cases. When available, confirm the test report shows all integration tests executed rather than skipped.

## Suggested review checklist

- **Transactionality:** Confirm `DrawService.draw` is invoked through Spring's transaction proxy and that request/idempotency record, quota, inventory, history, and persisted response share one transaction.
- **Duplicate requests:** Check the unique key scope `(campaign_id, user_id, idempotency_key)`, same-count replay, different-count conflict, and concurrent retry behavior against PostgreSQL.
- **Oversell protection:** Verify the conditional decrement and database constraint prevent negative inventory even with multiple API instances.
- **Quota race:** Verify atomic upsert semantics under simultaneous draws for the same principal and campaign. Check a failed reservation changes neither quota nor stock.
- **Configuration races:** Verify `FOR SHARE` on a draw and `FOR UPDATE` during configuration replacement protect the configuration/prize snapshot.
- **Probability math:** Verify all weights sum to 10,000 basis points and boundary rolls map to exactly the intended intervals. Check sold-out outcomes do not reroll.
- **Identity and authorization:** Confirm the client cannot choose a different draw user, campaign writes are admin-only, credentials are not committed, and the sample HTTP Basic mechanism is not represented as production SSO.
- **Error behavior:** Confirm validation, authentication, authorization, conflict, quota, and unexpected errors have appropriate status codes and do not leak internals.
- **Schema and migrations:** Review foreign keys, unique constraints, checks, rollback implications, migration ordering, and whether history retention/cleanup is needed for the intended scale.
- **Operational suitability:** Assess PostgreSQL HA, connection pool sizing, secrets, TLS, timeouts, rate limiting, backup/restore, idempotency retention, and monitoring for the actual deployment.

## Explicit limitations to assess

- User accounts are in-memory demo identities; they are not durable application users and are not shared by an identity provider.
- PostgreSQL is the consistency and availability dependency. The Compose file starts a single local database and does not provide database failover.
- Basic authentication is only appropriate for local demonstration or use behind correctly configured TLS and a suitable credential-management policy.
- Idempotency records and draw history currently have no retention/archival job. Add a documented retention policy before sustained production use; deleting idempotency records too early can make an old retry execute again.
- The application has a single prize outcome per draw. It does not model fulfillment, coupon issuance, payment, outbox delivery, or compensation with external systems.
- Campaign reads use simple queries and are not paginated; add pagination and query limits if campaign/prize counts grow substantially.

Reviewers should report findings with file/line references, impact, confidence, and a concrete reproduction or invariant that is violated. Suggested tools and models can inspect the repository independently; no external review result is claimed by this project.
