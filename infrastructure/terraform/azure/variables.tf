variable "location" {
  description = <<-EOT
    Azure region. Pick one with B2ats_v2 + AKS free-tier control plane
    availability. Azure periodically closes specific regions to new
    customers on brand-new subscriptions ("not currently accepting new
    customers") — westeurope was closed for this subscription as of
    2026-07-29 while eastus worked, but that's a live, changing
    restriction, not a fixed fact — re-verify if this ever fails the same
    way again rather than assuming eastus is permanently safe.
  EOT
  type        = string
  default     = "eastus"
}

variable "resource_group_name" {
  type    = string
  default = "lmdb-demo"
}

variable "cluster_name" {
  type    = string
  default = "lmdb-aks"
}

variable "vm_size" {
  description = "Node size for the single demo node — NOT free-tier B-series, see modules/cluster-aks/variables.tf for why (AKS's own 4GB minimum ruled out B2ats_v2, this subscription's region-level allow-list ruled out the whole B-series family, and F2as_v7's actual specs turned out to be 8GB not 4GB). Standard_D2ls_v7 was the confirmed-allowed size for the movie-only slice; bumped to Standard_D4ls_v7 (#151) for the full local-parity service set (actor/user/ai-service + Postgres + Ollama) — confirmed within the subscription's 4-vCPU Dlsv7-family quota."
  type        = string
  default     = "Standard_D4ls_v7"
}

variable "node_count" {
  type    = number
  default = 1
}

variable "enable_node_public_ip" {
  description = "See modules/cluster-aks: gives the node a public IP so the gateway is reachable without a Standard LB/NAT gateway. Small non-zero cost while the cluster is up — see README.md."
  type        = bool
  default     = true
}

variable "demo_inbound_port" {
  description = "NodePort the gateway is exposed on; must match the Service patch in infrastructure/kubernetes/overlays/azure."
  type        = number
  default     = 30080
}

variable "alert_emails" {
  description = "Who gets the zero-spend budget alert. No default — this is a real email, supply it via terraform.tfvars (see README.md)."
  type        = list(string)
}

variable "budget_amount" {
  description = "Zero-spend tripwire threshold in the subscription's billing currency."
  type        = number
  default     = 1
}

variable "budget_start_date" {
  description = "First of the current month, RFC3339 — see modules/budget-guard and README.md. No default: it depends on when you actually apply."
  type        = string
}

variable "tags" {
  type = map(string)
  default = {
    project     = "filmpire"
    managed-by  = "terraform"
    environment = "demo"
  }
}
