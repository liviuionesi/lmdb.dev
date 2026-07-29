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

# No node_public_ip output — see main.tf's closing comment for why (the
# obvious data.azurerm_public_ips approach silently pointed at the wrong
# resource on a live cluster). Get it from `kubectl get nodes -o wide`
# instead, after `az aks get-credentials`.
