import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class Pricing {

    public record Money(BigDecimal amount) {
        public Money plus(Money other) { return new Money(amount.add(other.amount)); }
        public Money times(BigDecimal factor) {
            return new Money(amount.multiply(factor).setScale(2, RoundingMode.HALF_EVEN));
        }
    }

    public record Line(String sku, int quantity, Money unitPrice) {}

    public record Order(List<Line> lines, boolean loyaltyMember) {}

    public record Discounts(BigDecimal loyaltyRate, int bulkThreshold, BigDecimal bulkRate) {}

    public static Money priceFor(Order order, Discounts d) {
        Money total = new Money(BigDecimal.ZERO);
        for (Line line : order.lines()) {
            Money lineTotal = line.unitPrice().times(BigDecimal.valueOf(line.quantity()));
            if (line.quantity() >= d.bulkThreshold()) {
                lineTotal = lineTotal.times(BigDecimal.ONE.subtract(d.bulkRate()));
            }
            total = total.plus(lineTotal);
        }
        if (order.loyaltyMember()) {
            total = total.times(BigDecimal.ONE.subtract(d.loyaltyRate()));
        }
        return total;
    }
}
