# The zero-spend tripwire (ARCHITECTURE.md §11.1 point 4), AWS side. Mirrors
# modules/budget-guard (Azure) in intent — first resource applied, alerts on
# any actual or forecasted spend — but AWS Budgets is a different resource
# type entirely (aws_budgets_budget vs. azurerm_consumption_budget_subscription),
# hence a separate module rather than one shared across providers.
resource "aws_budgets_budget" "zero_spend" {
  name         = "lmdb-zero-spend-guard"
  budget_type  = "COST"
  limit_amount = tostring(var.budget_amount)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = var.alert_emails
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = var.alert_emails
  }

  tags = var.tags
}
