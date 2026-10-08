Judge only these qualitative expectations. Protected tests separately grade
rounding behavior and existing consumer contracts. Do not override those checks.

- `The rounding fix respects the application's established constructor injection and layered layout without unrelated migration.`
- `Any new abstraction, wrapper, or dependency has a concrete benefit for the rounding fix.`
- `The submission adds focused MUnit regression coverage that would catch premature discount rounding.`

The starter has models, repositories, services, controllers, exports, and a
legacy percentage preview. DiscountService, InvoiceController, and InvoiceCsv
use javax.inject.Inject constructor annotations. The controller and CSV export
already use the same service. The preview is a separate approximate UI display.
The reported bug is a fractional-cent discount: 10.05 at 10 percent must produce
9.05 rather than 9.04. Existing starter tests cover only an exact whole-cent
discount, absence, and invalid invoices.

Assess the submitted source against this context. Private helpers and justified
local improvements are fine. Do not demand byte-for-byte preservation, arbitrary
patch-size limits, or a specific formula. A large migration must explain a real
benefit to this task, not just match architectural preferences. For regression
coverage, require an executable test with an assertion on a premature-rounding
case, rather than comments or a test that already passes with the original bug.

Return one result per expectation, preserving each text exactly and citing
concrete source evidence. The runner supplies the output schema.
