variable "alert_emails" {
  description = "Email addresses notified when actual or forecasted spend crosses the budget threshold."
  type        = list(string)

  validation {
    condition     = length(var.alert_emails) > 0
    error_message = "At least one alert email is required — this is the whole point of the tripwire."
  }
}

variable "budget_amount" {
  description = "Monthly spend threshold in USD that triggers the alert. Kept low (not zero — AWS Budgets requires a positive amount) so any real spend fires it immediately."
  type        = number
  default     = 1
}

variable "tags" {
  description = "Tags applied to the budget."
  type        = map(string)
  default     = {}
}
