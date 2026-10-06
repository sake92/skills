package inventory

final case class HttpResponse(status: Int, body: String)

final class ReservationHttp(inventory: Inventory):
  def post(productId: String, quantity: Int): HttpResponse =
    if quantity <= 0 then HttpResponse(400, "quantity must be positive")
    else
      inventory.reserve(productId, quantity) match
        case Right(remaining) => HttpResponse(200, s"remaining=$remaining")
        case Left(ReservationError.InvalidQuantity) => HttpResponse(400, "quantity must be positive")
        case Left(ReservationError.UnknownProduct) => HttpResponse(404, "product not found")
        case Left(ReservationError.InsufficientStock) => HttpResponse(409, "insufficient stock")
