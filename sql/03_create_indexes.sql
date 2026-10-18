-- Run against the filtered products.db database.
CREATE INDEX idx_code ON products(code);
CREATE INDEX idx_product_name ON products(product_name);
CREATE INDEX idx_brands ON products(brands);
