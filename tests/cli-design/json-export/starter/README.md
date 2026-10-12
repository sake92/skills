# Stockdesk

Small Scala CLI for exporting a warehouse inventory. No external service is required.

The source is a JSON array of records with string `id`, string `name`, and integer `quantity`. Input ordering is retained. Quantities may be zero. Item names can contain arbitrary Unicode and escaped JSON characters.

## Existing interface

`stockdesk export SOURCE [--output PATH] [--force] [--no-color]`

Default output is a human banner followed by a tab-separated table. `--output` writes the table without its banner to a file and prints a completion message. Interactive overwrite confirmation defaults to no; `--force` is the unattended replacement option. Help and version do not require a source. Usage failures exit 2; input/output failures and declined replacements exit 1.

Default invocation, table bytes, and the table-file completion message are existing contracts used by operators and tests.

## Development

Run tests: `scala-cli test . --server=false`

Run the CLI: `scala-cli run . --server=false --main-class inventory.InventoryCli -- export stock.json`

The job's proposed invocation is `stockdesk export stock.json --format json`. The job expects one JSON array with the same three fields and types as the source, including an empty array for an empty inventory. JSON mode should also support the existing file-output option.
