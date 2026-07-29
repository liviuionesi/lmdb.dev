variable "cluster_name" {
  description = "AKS cluster name."
  type        = string
  default     = "filmpire-aks"
}

variable "location" {
  type = string
}

variable "resource_group_name" {
  description = "Resource group to create the cluster in — pass through module.network's output so networking and cluster share one group."
  type        = string
}

variable "subnet_id" {
  description = "Subnet (from modules/network) the default node pool attaches to."
  type        = string
}

variable "vm_size" {
  description = "Free-tier-eligible burstable B-series size for the single node (ARCHITECTURE.md §11.2)."
  type        = string
  default     = "Standard_B2ats_v2"
}

variable "node_count" {
  description = "Node count for the default pool. One node only — this is a demo cluster, not HA."
  type        = number
  default     = 1
}

variable "enable_node_public_ip" {
  description = <<-EOT
    Gives each node its own public IP so the gateway is reachable on its
    NodePort without a Standard Load Balancer or NAT gateway (issue #26
    scope update). Note this IS a small billable resource (~$0.005/hr per
    Azure's Standard Public IP pricing) — not literally $0 while the
    cluster is up, but the ephemeral apply→demo→destroy cycle keeps total
    exposure to a few cents. See infrastructure/terraform/README.md.
  EOT
  type        = bool
  default     = true
}

variable "tags" {
  type    = map(string)
  default = {}
}
