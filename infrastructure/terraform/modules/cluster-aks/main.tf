resource "azurerm_kubernetes_cluster" "this" {
  name                = var.cluster_name
  location            = var.location
  resource_group_name = var.resource_group_name
  dns_prefix          = var.cluster_name
  sku_tier            = "Free" # free control plane (ARCHITECTURE.md §11.2)
  node_resource_group = "${var.resource_group_name}-nodes"

  default_node_pool {
    name                   = "default"
    node_count             = var.node_count
    vm_size                = var.vm_size
    vnet_subnet_id         = var.subnet_id
    node_public_ip_enabled = var.enable_node_public_ip
  }

  identity {
    type = "SystemAssigned"
  }

  network_profile {
    network_plugin      = "azure"
    network_plugin_mode = "overlay"
    # network_policy left unset: no policy plugin (Calico/Cilium) — not
    # needed for a single-node demo cluster, and current azurerm no longer
    # accepts "none" as an explicit value here (omission means the same).
    # AKS no longer offers a "basic" SKU option for new clusters — Standard
    # is the only choice, and it's provisioned automatically inside
    # node_resource_group purely for outbound SNAT (no inbound rules, since
    # we never create a Kubernetes Service of type LoadBalancer). That's
    # different from the Standard LB / NAT gateway issue #26 says not to
    # add ourselves for INBOUND — this is AKS's own mandatory plumbing, and
    # with zero LB rules it carries no meaningful hourly charge.
    load_balancer_sku = "standard"
  }

  # No ACR / kubelet_identity role assignment: images come from ghcr.io,
  # which is public and needs no pull credentials (issue #26 scope update
  # replaced modules/registry with modules/budget-guard for this reason).

  tags = var.tags
}

# azurerm_kubernetes_cluster doesn't expose per-node public IPs directly —
# AKS creates the node pool's VMSS inside node_resource_group, and the IP
# resource names aren't predictable in advance. azurerm_public_ips (plural)
# exists for exactly this: list what's attached in that resource group
# after the cluster (and therefore the VMSS + its IP) exists.
data "azurerm_public_ips" "nodes" {
  resource_group_name = azurerm_kubernetes_cluster.this.node_resource_group
  attachment_status   = "Attached"
}
