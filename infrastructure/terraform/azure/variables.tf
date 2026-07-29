variable "location" {
  description = "Azure region. Pick one with B2ats_v2 + AKS free-tier control plane availability."
  type        = string
  default     = "westeurope"
}

variable "resource_group_name" {
  type    = string
  default = "filmpire-demo"
}

variable "cluster_name" {
  type    = string
  default = "filmpire-aks"
}

variable "vm_size" {
  description = "Free-tier-eligible burstable B-series size for the single demo node."
  type        = string
  default     = "Standard_B2ats_v2"
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
