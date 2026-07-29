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

output "node_public_ip" {
  description = "Public IP of the demo node once the node pool is up. Null immediately after apply — see README.md for the retry note."
  value       = module.cluster_aks.node_public_ip
}

output "gateway_url" {
  description = "Where to curl once `kubectl apply -k overlays/azure` has run — null until node_public_ip is populated."
  value       = module.cluster_aks.node_public_ip != null ? "http://${module.cluster_aks.node_public_ip}:${var.demo_inbound_port}" : null
}
