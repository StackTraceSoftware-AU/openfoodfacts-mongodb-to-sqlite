#!/usr/bin/env python3
"""Read-only product-name quality scan for Wotsin's SQLite products database."""
import argparse
import csv
import re
import sqlite3
from collections import Counter
from pathlib import Path

HTML_ENTITY = re.compile(r"&(?:#\d+|#x[0-9a-fA-F]+|[A-Za-z][A-Za-z0-9]+);")
PRICE = re.compile(r"(?i)(?:\b(?:any\s+\d+\s+for|special\s+offer|save\s+\d+%|buy\s+one\s+get\s+one)\b|(?:[$£€]\s*\d+(?:[.,]\d{1,2})?))")
BARCODE = re.compile(r"[0-9]{6,14}")


def classify(name):
    if name is None or not name.strip():
        return 'REJECT_CANDIDATE', ['Empty name']
    value = name.strip()
    letters = sum(c.isalpha() for c in value)
    digits = sum(c.isdigit() for c in value)
    if letters == 0:
        if BARCODE.fullmatch(value) or (digits >= 5 and not any(c.isalpha() for c in value)):
            return 'REJECT_CANDIDATE', ['Numeric identifier without a product name']
        return 'REJECT_CANDIDATE', ['No alphabetic characters']

    reasons = []
    if HTML_ENTITY.search(value):
        reasons.append('HTML entity in name')
    if value[0] in ',./\\()[]{}<>#*;:!?"\'':
        reasons.append('Unusual leading punctuation')
    if PRICE.search(value):
        reasons.append('Possible price or promotional text')
    if len(value) >= 12:
        punctuation = sum(not (c.isalnum() or c.isspace()) for c in value)
        if punctuation / len(value) >= 0.25:
            reasons.append('High punctuation density')
    if len(value) >= 15 and len(value.split()) <= 2 and letters < len(value) * 0.45:
        reasons.append('Low letter density')
    if any(ord(c) < 32 and c not in '\t\n\r' for c in value):
        reasons.append('Control characters')
    if reasons:
        return 'REVIEW', reasons
    return None, []


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('database', type=Path, help='Existing SQLite database (products.db)')
    parser.add_argument('--output', type=Path, default=Path('suspicious_product_names.csv'), help='CSV report destination')
    args = parser.parse_args()

    if not args.database.is_file():
        parser.error(f'Database not found: {args.database}')
    if args.output.resolve() == args.database.resolve():
        parser.error('CSV output must not overwrite the database')

    # mode=ro prevents accidental database modifications and creation of missing databases.
    uri = args.database.resolve().as_uri() + '?mode=ro'
    totals = Counter()
    with sqlite3.connect(uri, uri=True) as connection:
        with args.output.open('w', encoding='utf-8-sig', newline='') as file:
            writer = csv.writer(file)
            writer.writerow(['id', 'code', 'product_name', 'classification', 'reason'])
            for row_id, code, name in connection.execute('SELECT id, code, product_name FROM products'):
                totals['scanned'] += 1
                classification, reasons = classify(name)
                if classification:
                    writer.writerow([row_id, code, name, classification, '; '.join(reasons)])
                    totals[classification] += 1
    print(f"Scanned: {totals['scanned']:,}")
    print(f"Reject candidates: {totals['REJECT_CANDIDATE']:,}")
    print(f"Review: {totals['REVIEW']:,}")
    print(f"Report: {args.output.resolve()}")
    print('No database rows were modified.')


if __name__ == '__main__':
    main()
