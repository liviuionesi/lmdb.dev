data "azurerm_subscription" "current" {}

# The zero-spend tripwire (ARCHITECTURE.md §11.1 point 4). Scoped to the
# whole subscription rather than a resource group so it catches spend from
# ANY resource this composition creates, and so it can be applied before
# network/cluster-aks exist at all (issue #26 scope update: "applied
# first"). azurerm_consumption_budget_subscription doesn't take a `tags`
# argument — the Azure Consumption Budgets API doesn't support tagging —
# so there's nothing to apply var.tags to here.
resource "azurerm_consumption_budget_subscription" "zero_spend" {
  name            = "lmdb-zero-spend-guard"
  subscription_id = data.azurerm_subscription.current.id

  amount     = var.budget_amount
  time_grain = "Monthly"

  time_period {
    start_date = var.budget_start_date
  }

  notification {
    enabled        = true
    threshold      = 100.0
    operator       = "GreaterThan"
    threshold_type = "Actual"
    contact_emails = var.alert_emails
  }

  notification {
    enabled        = true
    threshold      = 100.0
    operator       = "GreaterThan"
    threshold_type = "Forecasted"
    contact_emails = var.alert_emails
  }
}
