variable "resource_group_name" {
  description = "Name of the resource group this module creates and scopes all networking resources to."
  type        = string
}

variable "location" {
  description = "Azure region for the resource group and all networking resources."
  type        = string
}

variable "vnet_address_space" {
  description = "CIDR for the VNet."
  type        = string
  default     = "10.10.0.0/16"
}

variable "aks_subnet_prefix" {
  description = "CIDR for the AKS node subnet, carved out of vnet_address_space."
  type        = string
  default     = "10.10.1.0/24"
}

variable "demo_inbound_port" {
  description = <<-EOT
    NodePort the gateway is exposed on for demos (ARCHITECTURE.md §11.1 point 5 /
    issue #26 scope update: no Standard LB, no NAT gateway — reach the gateway
    directly on the node's public IP). Must match the NodePort configured in
    infrastructure/kubernetes/overlays/azure's Service patch.
  EOT
  type        = number
  default     = 30080
}

variable "tags" {
  description = "Tags applied to every resource this module creates."
  type        = map(string)
  default     = {}
}
