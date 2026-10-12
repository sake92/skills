# Layered dependency knot

| Configuration | Protected checks | Candidate time | Candidate tokens |
| --- | ---: | ---: | ---: |
| With skill | 7/7 | 202.7 s | 60,040 |
| Without skill | 5/7 | 155.5 s | 33,747 |

Score gain: **+28.6 percentage points** (71.4% → 100%).

## Task and fixture

The [customer directory](../../../tests/codeps/dependency-knot/starter/) has 12
production files across API, services, repositories, DAO, jobs and application
wiring. Its two visible tests pass before the task. HTTP types and policy create
a four-package knot, while the file graph is acyclic. The task asks for a focused
boundary correction with preserved controller/job behavior and dependency
verification, without naming the desired implementation.

## What differed

Both candidates introduced a domain customer model and preserved the tested
directory, export and job behavior. Baseline still imports the HTTP input:

```scala
import app.api.CustomerInput
import app.domain.Customer
import app.repositories.CustomerRepository

final class CustomerService(repository: CustomerRepository):
  def create(input: CustomerInput): Either[String, Customer] =
```

That leaves an API/service package cycle. With-skill moves the service command
into the domain:

```scala
import app.domain.{Customer, NewCustomer}
import app.repositories.CustomerRepository

final class CustomerService(repository: CustomerRepository):
  def create(input: NewCustomer): Either[String, Customer] =
```

The API keeps its original input model and maps it at the controller boundary:

```scala
def create(input: CustomerInput): HttpResult =
  service.create(NewCustomer(input.id, input.name, input.region)) match
```

It also labels uncommitted post-change reports as `worktree:boundary` and
`worktree:boundary-final`. Baseline records both reports under unchanged `HEAD`.

## Grading

| Protected criterion | With skill | Without skill |
| --- | --- | --- |
| Directory ordering, normalization, lookup and missing-customer behavior | Pass | Pass |
| Creation validation, duplicate rejection, output and later lookup | Pass | Pass |
| Ordered CRM partial results and shared directory state | Pass | Pass |
| CSV and reconciliation contracts | Pass | Pass |
| Independently rebuilt package/file graphs are acyclic | Pass | Fail |
| Submitted final report matches the protected fresh graph | Pass | Pass |
| Before/after scope and analyzed-state labeling | Pass | Fail |

The last two failures are related to the remaining cycle and verification
workflow, rather than two independent behavior defects. Baseline's final report
accurately shows the remaining cycle; the agent nevertheless declares the
refactoring complete. Direct source compatibility of every job constructor is
outside the protected consumer checks.

## Limits and cost

The final with-skill smoke was paired with the previously qualified baseline after
verifying identical starter trees and task metadata. The skill was tuned on the
frozen case; this is adaptive development evidence, not a held-out estimate.
It used 26,293 more candidate tokens and took 47.2 s longer in this pair.
No stable quality or efficiency rate follows from one run.

See the [workflow and grader validation](../../../tests/codeps/README.md).
