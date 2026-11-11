# Transaction compliance

Reads `transactions.json` and `rules.csv`, then prints the rules breached by each transaction.

## Run

Requires Java 17 and Maven. From the project folder, run with the sample files:

```sh
mvn -q exec:java
```

To use other files:

```sh
mvn -q exec:java -Dexec.args="path\to\transactions.json path\to\rules.csv"
```

The report is printed to stdout. Warnings and errors are printed to stderr.

## Tests

```sh
mvn -q test
```

## Assumptions and input handling

- The first CSV row is a header; blank rows are ignored. Quoted values can contain commas.
- `gt` and `lt` are strict numeric comparisons. `eq`, `neq`, and `in` are case-sensitive text comparisons. Spaces around `in` list items are trimmed, and empty items are ignored.
- `timestamp.hour` uses UTC when the timestamp includes a time-zone offset. Without an offset, the hour is used as written and a warning is printed. An invalid timestamp does not match hour rules.
- Text comparisons on `amount` use a plain number string: `15000.00` becomes `15000`.
- A missing field does not match a rule, including `neq`.
- A rule with a missing ID, too few columns, an unknown field or operator, or a non-numeric `gt`/`lt` value is skipped with a warning. An unsupported severity is reported, but the rule is still used.
- Non-object entries in the JSON `transactions` array are skipped. Transactions with missing or invalid values are retained with warnings; missing values do not match rules.
- A missing input file, invalid JSON, or a JSON document without a `transactions` array causes the app to exit with an error.
- Transactions are printed in file order. Breached rules are sorted by `rule_id`; a transaction with no breaches shows `none`. The report columns are separated by four spaces.

## Next steps

With more time, I would check for duplicate rule IDs and add tests for more malformed input cases.

## Tools and time

Cursor and Copilot in VS Code assisted with development. I translated the brief into a clear evaluation model, built the JSON/CSV processing and rule-matching flow, and handled edge cases such as malformed input, strict comparison boundaries, and UTC timestamp offsets. I added unit tests for these behaviors, reviewed the AI-assisted changes, and checked the report against the sample data.

Time spent: about two hours.
