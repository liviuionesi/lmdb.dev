output "resource_group_name" {
  description = "Name of the resource group created for this environment — consumed by cluster-aks so all resources land in the same group."
  value       = azurerm_resource_group.this.name
}

output "location" {
  value = azurerm_resource_group.this.location
}

output "vnet_id" {
  value = azurerm_virtual_network.this.id
}

output "aks_subnet_id" {
  value = azurerm_subnet.aks.id
}
