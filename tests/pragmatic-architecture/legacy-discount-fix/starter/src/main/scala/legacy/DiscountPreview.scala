package legacy

// The admin preview is an approximate percentage display, not an invoice total.
// The existing UI calls this API and formats its Double result itself.
object DiscountPreview:
  def percentageLabel(percent: Double): String = percent.toString + "%"
