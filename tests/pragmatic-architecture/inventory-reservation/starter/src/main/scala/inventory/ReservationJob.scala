package inventory

enum JobResult:
  case Reserved(remaining: Int)
  case Rejected(reason: ReservationError)

final class ReservationJob(inventory: Inventory):
  def run(productId: String, quantity: Int): JobResult =
    inventory.reserve(productId, quantity) match
      case Right(remaining) => JobResult.Reserved(remaining)
      case Left(reason)     => JobResult.Rejected(reason)
