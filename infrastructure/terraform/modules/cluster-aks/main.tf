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

# Deliberately NOT trying to output the node's public IP via Terraform.
# Tried data.azurerm_public_ips (plural) first — wrong tool: it only
# enumerates standalone Microsoft.Network/publicIPAddresses resources, so
# on a live cluster it silently returned the AKS-managed outbound
# Standard LB's IP (also standalone) instead of the actual
# node_public_ip_enabled IP, which is a VMSS INSTANCE-level public IP —
# a different kind of resource, invisible to that data source, only
# enumerable via `az vmss list-instance-public-ips` (confirmed against a
# real cluster on 2026-07-29: the LB's IP never accepts NodePort traffic,
# only the instance IP does — this isn't a cosmetic difference, the wrong
# IP output would send you to a dead end). No clean azurerm data source
# covers VMSS instance public IPs. Get the real one from
# `kubectl get nodes -o wide` after `az aks get-credentials` — see
# infrastructure/terraform/README.md step 4 — rather than trust a
# Terraform output that's already proven capable of pointing at the wrong
# resource.
