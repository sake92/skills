Finish the in-memory customer repository in the starter project.

The application needs to:

- look up a customer by ID;
- look up a customer by email;
- list active customers in a region, preserving their input order.

Return `None` when a lookup has no match and an empty list when a region has no
active customers. Add MUnit coverage for the required behavior and run
`scala-cli test . --server=false`.
