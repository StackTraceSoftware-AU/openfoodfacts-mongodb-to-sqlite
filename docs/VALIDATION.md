# Validation notes

- Java exporter: user-reported successful full extraction on refreshed Open Food Facts data, October 2026; precise full-run counters not supplied for this release.
- Earlier smoke tests: 100 processed/100 exported, SQLite count 100; 1,100 processed/1,078 exported/22 rejected, SQLite count 1,078.
- Python validator: 533,646 rows scanned; 39 reject candidates, 387 review cases; no database modifications.
- Flags were inspected and included false positives; no automatic deletion is implemented.
- No database contents or rejected-code logs are distributed.
