# negative/Pricing.java — review WITHOUT the skill (model: haiku)

- **Money record (line 7)**: No validation to prevent negative amounts; add a compact constructor to enforce `amount.signum() >= 0`.
- **priceFor() method (line 20)**: No null checks for parameters `order` and `d`; add `Objects.requireNonNull()` calls at method entry.
- **Order.lines field (line 16)**: No null check before iteration on line 22; guard against null list with `if (order.lines() == null)` or validate at record construction.
- **Discounts record (line 18)**: No validation that `loyaltyRate` and `bulkRate` are in range [0, 1]; add a compact constructor to enforce `0 <= rate <= 1`.
- **Discounts.bulkThreshold (line 18)**: No validation that threshold is positive; add a compact constructor to enforce `bulkThreshold > 0`.
- **Money.plus() method (line 8)**: No rounding applied, while `times()` (line 10) rounds to 2 decimals; apply consistent rounding in `plus()` or document why one operation rounds and the other doesn't.
- **Discount application order (lines 24-31)**: Loyalty discount is applied after bulk discount, making the final price dependent on order; document explicitly which discount should apply first or add a parameter to control order.
- **Money.times() rounding (line 10)**: Rounding to 2 decimals after every multiplication can accumulate errors in chains of operations; consider rounding only at final result or use a higher intermediate precision.
