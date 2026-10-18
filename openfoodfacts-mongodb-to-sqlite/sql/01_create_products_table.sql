-- Run against a new, empty products.db SQLite database.
-- Derived from the original Wotsin extraction procedure.
CREATE TABLE products (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code TEXT,
    product_name TEXT,
    quantity TEXT,
    brands TEXT,
    labels_tags TEXT,
    ingredients_tags TEXT,
    allergens_tags TEXT,
    traces_tags TEXT,
    additives_tags TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
);
