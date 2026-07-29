variable "alert_emails" {
  description = "Email addresses notified when actual or forecasted spend crosses the budget threshold."
  type        = list(string)

  validation {
    condition     = length(var.alert_emails) > 0
    error_message = "At least one alert email is required — this is the whole point of the tripwire."
  }
}

variable "budget_amount" {
  description = "Monthly spend threshold in the subscription's billing currency that triggers the alert. Kept low (not zero — Azure Consumption Budgets require a positive amount) so any real spend fires it immediately."
  type        = number
  default     = 1
}

variable "budget_start_date" {
  description = <<-EOT
    First day of the budget's monthly period, RFC3339, e.g. "2026-07-01T00:00:00Z".
    Azure requires this to be the first of a month and there's no reliable
    "today" primitive in Terraform (timestamp() changes every plan), so it's
    supplied explicitly — see infrastructure/terraform/README.md. Because the
    cluster is ephemeral (create → demo → destroy the same session,
    ARCHITECTURE.md §11.1 point 3), this is normally just "the first of the
    current month" at apply time.
  EOT
  type        = string
}
