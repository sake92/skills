package inventory

enum ReservationError:
  case InvalidQuantity, UnknownProduct, InsufficientStock

final class Inventory(initialStock: Map[String, Int]):
  require(initialStock.values.forall(_ >= 0), "Initial stock must be nonnegative")
  private var stock = initialStock

  def available(productId: String): Option[Int] = stock.get(productId)

  def reserve(productId: String, quantity: Int): Either[ReservationError, Int] =
    stock.get(productId) match
      case None => Left(ReservationError.UnknownProduct)
      case Some(available) =>
        val remaining = available - quantity
        stock = stock.updated(productId, remaining)
        Right(remaining)
