#!/usr/bin/env python3
"""
Generates a SYNTHETIC subscription price history for training the price forecast model.

Why synthetic: the real price_log table only stores price CHANGES and holds a handful of rows, which is
far too little to train or evaluate a regression model. This script invents plausible services whose
prices rise over time, following simple rules per category (how often they hike, how much). The model
therefore learns patterns that exist because we put them here: report it as a pipeline demonstration on
synthetic data, never as evidence about real market behaviour.

Rules (all random draws are seeded, so the output is reproducible):
  * Each category has a typical gap between hikes and a typical hike size.
  * Cheaper services tend to hike by a larger percentage than expensive ones.
  * Each service has its own "personality" (fast or slow, big or small hikes), and each hike is partly
    a repeat of the previous one, plus noise.
  * Prices are rounded to rupee price points (for example 199, 499, 649).

Usage:  python data/generate_price_history.py
Output: backend/src/main/resources/ml/price_history_synthetic.csv  (service,category,date,price)
"""
import csv
import math
import random
from datetime import date, timedelta

SEED = 42
END = date(2026, 9, 30)
OUTPUT = "backend/src/main/resources/ml/price_history_synthetic.csv"

# category: (services, gap months (low, high), hike fraction (low, high), starting price (low, high))
CATEGORIES = {
    "Entertainment": (22, (10, 20), (0.08, 0.25), (99, 699)),
    "Productivity":  (14, (12, 24), (0.05, 0.15), (150, 1500)),
    "Fitness":       (8,  (9, 15),  (0.06, 0.12), (500, 2000)),
    "Utilities":     (8,  (18, 36), (0.10, 0.30), (70, 400)),
    "Other":         (8,  (12, 24), (0.05, 0.20), (100, 900)),
}


def price_point(x: float) -> int:
    """Round to a believable rupee price (ends in 9 below 1000, otherwise a round 50 minus 1)."""
    if x < 1000:
        return max(49, int(round(x / 10.0)) * 10 - 1)
    return int(round(x / 50.0)) * 50 - 1


def make_service(rng, category, gap_range, hike_range, price_range):
    low, high = price_range
    price = price_point(math.exp(rng.uniform(math.log(low), math.log(high))))
    start = date(2019, 1, 1) + timedelta(days=rng.randint(0, 3 * 365))
    gap_pref = rng.uniform(*gap_range)                    # this service's usual gap in months
    hike_pref = rng.uniform(*hike_range)                  # this service's usual hike size
    target_changes = rng.randint(3, 7)

    points = [(start, price)]
    last_hike = hike_pref
    current = start
    for _ in range(target_changes):
        gap_months = max(6.0, rng.gauss(gap_pref, 2.5))
        current = current + timedelta(days=int(gap_months * 30.4375))
        if current > END:
            break
        # Cheaper services hike a bit more, hikes partly repeat the last one, plus noise.
        level_effect = 0.04 * (math.log(600) - math.log(price)) / math.log(10)
        hike = 0.5 * last_hike + 0.5 * hike_pref + level_effect + rng.gauss(0, 0.025)
        hike = min(max(hike, 0.02), 0.45)
        new_price = price_point(price * (1 + hike))
        if new_price <= price:
            new_price = price + 10
        points.append((current, new_price))
        last_hike = new_price / price - 1
        price = new_price
    return points


def main():
    rng = random.Random(SEED)
    rows = []
    for category, (count, gap_range, hike_range, price_range) in CATEGORIES.items():
        for i in range(1, count + 1):
            name = f"SYN-{category[:3].upper()}-{i:02d}"
            for d, p in make_service(rng, category, gap_range, hike_range, price_range):
                rows.append((name, category, d.isoformat(), p))
    with open(OUTPUT, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["service", "category", "date", "price"])
        writer.writerows(rows)
    services = {r[0] for r in rows}
    print(f"wrote {len(rows)} price points for {len(services)} synthetic services to {OUTPUT}")


if __name__ == "__main__":
    main()
