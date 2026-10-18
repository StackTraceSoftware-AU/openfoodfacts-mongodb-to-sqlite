# Operational workflow (public summary)

Adapted from the Wotsin data extraction procedure, October 2026. Paths below are examples; use your own local directories.

## 1. Restore

Download the current MongoDB dump from https://world.openfoodfacts.org/data and restore it into a local MongoDB instance. Verify the restored collection and indexes. The original operating procedure includes disk-space checks, logging, backups and service management; these are environment-specific and are not automated here.

## 2. Export

Create a **new** SQLite destination, then run the Java exporter with arguments such as:

```text
off.products /path/to/off_sqlite.db products
```

Check processed/exported/rejected/error totals against the SQLite row count, and sample rejected codes. Back up the intermediate export before further processing.

## 3. Subset

In a new `products.db`, run `sql/01_create_products_table.sql`, edit the source path in `sql/02_copy_filtered_products.sql`, and run the copy. The SQL selects specified country tags and nonempty ingredient tags.

## 4. Product-name review

```bash
python3 scripts/validate_product_names.py /path/to/products.db --output suspicious_product_names.csv
```

Review the CSV and manually assess suspicious names. The script is read-only and does **not** perform deletion. Do not assume all flagged names are invalid.

## 5. Index and back up

Create indexes using `sql/03_create_indexes.sql` after review. Validate the resulting database and make a backup before deploying it with the app.

The full private operating procedure also includes filesystem-specific backups and service commands. Those have not been included verbatim because they depend on the operator's machine.
