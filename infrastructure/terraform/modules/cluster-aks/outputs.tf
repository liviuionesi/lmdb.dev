output "cluster_name" {
  value = azurerm_kubernetes_cluster.this.name
}

output "node_resource_group" {
  value = azurerm_kubernetes_cluster.this.node_resource_group
}

output "kube_config_raw" {
  value     = azurerm_kubernetes_cluster.this.kube_config_raw
  sensitive = true
}

output "node_public_ip" {
  description = "Public IP of the (single) demo node, once provisioned. Null until the node pool has actually come up."
  value       = try(data.azurerm_public_ips.nodes.public_ips[0].ip_address, null)
}
