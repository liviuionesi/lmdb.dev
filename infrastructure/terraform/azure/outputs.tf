output "resource_group_name" {
  value = module.network.resource_group_name
}

output "cluster_name" {
  value = module.cluster_aks.cluster_name
}

output "kube_config_raw" {
  description = "Fallback kubeconfig from Terraform state. Prefer `az aks get-credentials` (see README.md) — it's the mechanism the AKS team actually keeps working across CLI/provider versions."
  value       = module.cluster_aks.kube_config_raw
  sensitive   = true
}

# No node_public_ip / gateway_url outputs — a live apply on 2026-07-29
# proved the natural Terraform-side way to compute these
# (data.azurerm_public_ips against the node resource group) returns the
# wrong IP: the AKS-managed outbound Load Balancer's address, which never
# accepts NodePort traffic, not the actual per-node address from
# node_public_ip_enabled (a VMSS instance-level public IP, a resource
# kind that data source can't see). Get the real address with
# `kubectl get nodes -o wide` after `az aks get-credentials` — see
# README.md step 4. Output demo_inbound_port below so you only have to
# supply the IP half.
output "demo_inbound_port" {
  value = var.demo_inbound_port
}
