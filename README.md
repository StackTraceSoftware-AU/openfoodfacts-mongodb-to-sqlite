# Open Food Facts MongoDB → SQLite pipeline

A practical data preparation workflow used to build the offline product database for [Wotsin](https://stacktracesoftware.com/). It combines a Java/MongoDB-to-SQLite batch exporter, SQL selection and indexing, and a read-only Python product-name quality scanner.

**Status:** Published portfolio project. The Java exporter was successfully tested against the complete Open Food Facts dataset. The Python validator was tested against 533,646 product records, identifying 39 rejection candidates and 387 records requiring review. The validator is read-only and makes no database modifications.

## Pipeline

1. Download and restore the Open Food Facts MongoDB dump into a local MongoDB instance.
2. Run the Java exporter to copy selected fields into an intermediate SQLite database, validating product codes and names and writing in 1,000-record batches.
3. Check export totals, inspect rejected records, and back up the intermediate database.
4. Use `sql/01_create_products_table.sql` and `sql/02_copy_filtered_products.sql` to create a smaller database for selected countries and records with ingredient tags.
5. Run the Python product-name validator against the subset and review its CSV report. **The validator does not change the database.**
6. Review any proposed removals manually, then apply `sql/03_create_indexes.sql` and back up the finished database.

## Java exporter

Requirements: local MongoDB server, Java 11+ and Maven, and the dependencies in `pom.xml`. The Java entry point is `mongodb2sqlite.MongoDB2SQLite`, located at `src/main/java/mongodb2sqlite/MongoDB2SQLite.java`.

Arguments:

```text
<database.collection> <sqlite-file> <table>
```

Example:

```text
off.products /path/to/off_sqlite.db products
```

Use a fresh destination database. The exporter **appends** to an existing destination table; it does not replace its contents. Individual rejected product codes are logged for manual data-quality checks. Earlier committed batches remain in the destination if a later batch fails.

## SQL subset and indexes

Run the numbered scripts in `sql/` in order, against a **new** `products.db`. Edit the source database path in `02_copy_filtered_products.sql` before running. This is a purpose-specific selection for Wotsin, not a general export of every Open Food Facts product.

## Read-only product-name validation

Python 3, standard library only:

```bash
python3 scripts/validate_product_names.py /path/to/products.db --output suspicious_product_names.csv
```

The scanner reads `id`, `code` and `product_name` and reports suspicious names with a classification and reason. `REJECT_CANDIDATE` and `REVIEW` are **flags, not deletion instructions**. It does not guarantee detection of every invalid name.

A real run scanned **533,646** subset rows and flagged **39 reject candidates** and **387 review cases** (426 total). These are counts of heuristic flags, not confirmed invalid records.

## Build

From the repository root, run `mvn package` to compile the Java source. Run the main class from an IDE or with a Java classpath containing the Maven dependencies. This repository does not bundle a runnable fat JAR.

## Data and attribution

This repository does not contain the Open Food Facts dump, product records, generated SQLite databases or personal filesystem backups. Open Food Facts data is maintained by its contributors and subject to its own data licence and attribution requirements. See [Open Food Facts data](https://world.openfoodfacts.org/data) for source and licensing details. The source code and the underlying product data are separate works.

## Limitations

- Local MongoDB connection and a fixed product-field schema.
- Product-name checks are heuristic and may flag legitimate names.
- Some records may be rejected based on character-set and other project-specific validation rules.
- No automated recovery of an interrupted export; start with a new destination for a clean refresh.
- The workflow includes manual validation and backup steps.

See `docs/workflow.md` for the operational outline.

## Code licensing

The code in this repository is licensed under the [MIT License](LICENSE). Copyright © 2026 Stack Trace Software. The Open Food Facts dataset is separately licensed; see the data attribution section above.
