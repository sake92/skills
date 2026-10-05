package customers

final case class Customer(
    id: String,
    email: String,
    region: String,
    active: Boolean
)
