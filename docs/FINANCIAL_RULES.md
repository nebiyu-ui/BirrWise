# Financial rules

These rules are implemented in `app/src/main/java/et/birrwise/app/domain` and covered by unit tests. Every balance, report, and budget in BirrWise must follow them.

## Money

- Amounts are whole numbers of minor units (for ETB, cents) in a `Long`. Floating point is never used for money.
- A transaction amount is always greater than zero. The transaction type decides whether it adds or subtracts.
- Adding or subtracting different currencies is an error. Arithmetic that would overflow stops with an error instead of wrapping around.
- Text shown to people (for example `1,234.56 ETB`) is produced by `MoneyFormatter`. Typed amounts are read by `MoneyParser`, which uses the dot as the decimal separator, accepts commas only as thousands separators in groups of three, and rejects anything that would need rounding.

## Effect of each transaction type

| Type | Account balance | Counts as income | Counts as expense |
|---|---|---|---|
| Income | plus | yes | no |
| Expense | minus | no | yes |
| Fee | minus | no | yes |
| Refund | plus | no | reduces expenses |
| Transfer in | plus | no | no |
| Transfer out | minus | no | no |
| Reversal | opposite of the original | opposite of the original | opposite of the original |

- A transfer between two accounts is two linked legs (out and in) with the same `transferGroupId`. Transfers are never income or expenses. A transfer leg with no partner is reported as unpaired so the person can review it.
- A transfer fee is a separate Fee transaction.
- A reversal points to the transaction it undoes. It undoes that transaction's effect for the reversal's own amount (so partial reversals work) and counts in the original's category. A reversal with no link, with an original that cannot be found, or that points to another reversal is not applied and is reported as a warning.

## Account balance

- Tracked balance = opening balance + effect of confirmed transactions on that account.
- The opening balance is the balance at its date. Transactions before that moment are already inside it and are not counted again. With no opening date, every transaction counts.
- A balance is calculated from recorded transactions. It is not a verified bank balance and must not be presented as one.

## Statuses and exclusions

- Pending-review transactions are not included in balances or reports. They are counted so screens can say how many were left out.
- A transaction marked as excluded from reports still changes the account balance but is left out of income, expense, and category totals. It is counted too.
- Transactions in another currency are never mixed in. Summaries are produced per currency.

## Periods and time zones

- Day, week, month, and year periods run from local midnight to the next local midnight in the user's time zone. The start is included and the end is excluded.
- The default zone for Ethiopia is Africa/Addis_Ababa (UTC+3, no daylight saving). A late evening in UTC can already be the next month in Addis Ababa.
- Weeks start on Monday unless another day is chosen.

## Reports

- Income, expenses, net cash flow, and category totals all come from `FinanceEngine`, so the dashboard and reports agree.
- Net cash flow = income minus expenses. Category totals are net of refunds and reversals. Categories with a zero total are left out.
- Warnings (unapplied reversals, unpaired transfer legs, left-out pending transactions) are returned with the numbers so screens can explain them.

## Budgets

- Percent used is rounded down to a whole number. The alert threshold is checked exactly, without rounding.
- Net spending below zero counts as zero spent. Overspending is shown separately from the remaining amount.

## Manual entries

- Every amount is in Ethiopian birr (ETB). The code supports other currencies underneath, but the app does not offer them.
- A person can enter income, expense, fee, and refund as single transactions, and a transfer as a linked pair.
- A transfer takes money from one account and puts it in another. An optional transfer fee is saved as a separate Fee transaction on the sending account, in the Bank fees category, so the combined balance falls by the fee only.
- Everything for one entry (both legs and the fee) is saved together or not at all.
- Income categories go with income. Expense categories go with expense, fee, and refund. Transfers use the built-in Transfers category.
- An entry dated today uses the current time. An entry for another day uses noon, so it never lands on the edge of an opening balance date. Editing an entry without changing its day keeps its time.
- An entry dated before an account's opening balance date is saved but does not change the tracked balance. The form says so.
- Marking an entry "needs review" saves it as pending. Pending entries are left out of balances and reports until the person confirms them.
- Transfers cannot be edited. To change one, delete it and enter it again. Deleting either leg deletes both.
