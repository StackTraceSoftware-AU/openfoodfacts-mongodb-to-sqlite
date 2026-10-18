-- IMPORTANT: edit the path below to point to the intermediate export database.
-- Run against the newly created products.db database.
-- Original selection logic, preserved for documentation.
ATTACH DATABASE '/path/to/off_sqlite.db' AS source;

INSERT INTO products (
    code, product_name, quantity, brands, labels_tags,
    ingredients_tags, allergens_tags, traces_tags, additives_tags
)
SELECT
    code, product_name, quantity, brands, labels_tags,
    ingredients_tags, allergens_tags, traces_tags, additives_tags
FROM source.products
WHERE (
    countries_tags LIKE '%en:australia%' OR
    countries_tags LIKE '%en:united-states%' OR
    countries_tags LIKE '%en:united-kingdom%' OR
    countries_tags LIKE '%en:new-zealand%' OR
    countries_tags LIKE '%en:canada%' OR
    countries_tags LIKE '%en:ireland%' OR
    countries_tags LIKE '%en:world%'
)
AND ingredients_tags IS NOT NULL
AND ingredients_tags != '';
