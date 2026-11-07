package et.birrwise.app.domain

enum class AccountType { BANK, MOBILE_WALLET, CASH, SAVINGS, OTHER }

enum class SourceType { MANUAL, SMS, CSV, PDF }

enum class CategoryType { INCOME, EXPENSE, TRANSFER }

enum class ImportStatus { IN_PROGRESS, COMPLETED, FAILED }

enum class BudgetPeriodType { MONTHLY }

enum class RuleMatchField { DESCRIPTION, MERCHANT, COUNTERPARTY, ANY }

enum class RuleMatchType { CONTAINS, EQUALS, STARTS_WITH }

/** ACCOUNT_BALANCE: progress is the tracked balance of the linked account. MANUAL: progress is typed in. */
enum class GoalTrackingMethod { ACCOUNT_BALANCE, MANUAL }
