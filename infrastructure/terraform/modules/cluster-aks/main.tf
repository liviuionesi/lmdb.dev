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

    # Without this, changing vm_size forces recreation of the whole cluster
    # (ForceNew on default_node_pool), destroying all live workloads/PVCs
    # instead of just the node pool. This makes a vm_size change a graceful
    # rotation instead: azurerm creates a temp pool under this name,
    # migrates workloads, then deletes the old one (#151, found before
    # applying the D2ls_v7 -> D4ls_v7 resize).
    temporary_name_for_rotation = "temp"

    # AKS silently applies this default server-side even when omitted here,
    # which without an explicit block causes perpetual drift on every plan
    # (Terraform sees state -> null, AKS reports null -> populated) --
    # found live on #26's second apply. Declaring it explicitly, matching
    # AKS's own default, makes plans stable again.
    upgrade_settings {
      max_surge = "10%"
    }
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

# AKS auto-creates a SECOND network security group on the node VMSS's own
# NIC (name pattern "aks-agentpool-<hash>-nsg", inside node_resource_group)
# whenever enable_node_public_ip is true — separate from and in ADDITION
# to modules/network's subnet-level NSG, starting with zero rules (deny
# all inbound from the internet by default). Found live on #26's apply:
# the subnet NSG's allow-gateway-nodeport rule alone was not enough —
# traffic was silently dropped at this second, AKS-managed layer, and
# `kubectl get pods` showing everything Ready gave no hint why the
# gateway was unreachable from outside the cluster. Both NSGs must allow
# the NodePort; this data source finds the auto-created one so we can
# open it too, entirely via Terraform, no manual `az network nsg rule
# create` step required after apply.
data "azurerm_resources" "node_nsg" {
  # resource_group_name already references azurerm_kubernetes_cluster.this,
  # which is enough for Terraform's dependency graph — an explicit
  # depends_on here made this data source (and everything reading it)
  # look "known after apply" on every plan that touches the cluster
  # resource at all, even for unrelated drift, which forced a spurious
  # destroy+recreate of the security rule below on every apply.
  resource_group_name = azurerm_kubernetes_cluster.this.node_resource_group
  type                = "Microsoft.Network/networkSecurityGroups"
}

resource "azurerm_network_security_rule" "allow_gateway_nodeport_on_node_nsg" {
  name                        = "allow-gateway-nodeport"
  priority                    = 100
  direction                   = "Inbound"
  access                      = "Allow"
  protocol                    = "Tcp"
  source_port_range           = "*"
  destination_port_range      = var.demo_inbound_port
  source_address_prefix       = "Internet"
  destination_address_prefix  = "*"
  resource_group_name         = azurerm_kubernetes_cluster.this.node_resource_group
  network_security_group_name = data.azurerm_resources.node_nsg.resources[0].name
}

resource "azurerm_network_security_rule" "allow_caddy_http_acme_on_node_nsg" {
  name                        = "allow-caddy-http-acme"
  priority                    = 110
  direction                   = "Inbound"
  access                      = "Allow"
  protocol                    = "Tcp"
  source_port_range           = "*"
  destination_port_range      = "80"
  source_address_prefix       = "Internet"
  destination_address_prefix  = "*"
  resource_group_name         = azurerm_kubernetes_cluster.this.node_resource_group
  network_security_group_name = data.azurerm_resources.node_nsg.resources[0].name
}

resource "azurerm_network_security_rule" "allow_caddy_https_on_node_nsg" {
  name                        = "allow-caddy-https"
  priority                    = 111
  direction                   = "Inbound"
  access                      = "Allow"
  protocol                    = "Tcp"
  source_port_range           = "*"
  destination_port_range      = "443"
  source_address_prefix       = "Internet"
  destination_address_prefix  = "*"
  resource_group_name         = azurerm_kubernetes_cluster.this.node_resource_group
  network_security_group_name = data.azurerm_resources.node_nsg.resources[0].name
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
