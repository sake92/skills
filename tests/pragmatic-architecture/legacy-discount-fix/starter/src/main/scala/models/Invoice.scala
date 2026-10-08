package models

final case class Invoice(id: String, subtotal: BigDecimal, discountPercent: BigDecimal)
