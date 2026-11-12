# Current HTTP interface

Compatibility baseline: endpoint declarations retained by this refactor.

| Method | Path |
|---|---|
| POST | `/api/auth/register` |
| POST | `/api/auth/login` |
| POST | `/api/auth/logout` |
| POST | `/api/auth/change-password` |
| GET | `/api/categories` |
| GET | `/api/bills` |
| POST | `/api/bills` |
| PUT | `/api/bills/{bill_id}` |
| DELETE | `/api/bills/{bill_id}` |
| POST | `/api/bills/{bill_id}/pay` |
| GET | `/api/summary` |
| GET | `/api/summary/history` |
| GET | `/api/budgets/{month}` |
| PUT | `/api/budgets/{month}` |
| GET | `/api/budgets/{month}/suggestions` |
| GET | `/api/chat/{sid}` |
| POST | `/api/chat/{sid}` |
| POST | `/api/chat/{sid}/receipt` |
| GET | `/api/transactions` |
| POST | `/api/transactions` |
| PUT | `/api/transactions/{rid}` |
| DELETE | `/api/transactions/{rid}` |

Request schemas live in moneybot/routes; inspect /openapi.json and /docs on the running server.
Keep request/response fields, status codes and {"message": ...} error envelopes compatible.
Bearer authentication and existing Idempotency-Key semantics remain unchanged.
