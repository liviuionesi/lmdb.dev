resource "azurerm_resource_group" "this" {
  name     = var.resource_group_name
  location = var.location
  tags     = var.tags
}

resource "azurerm_virtual_network" "this" {
  name                = "${var.resource_group_name}-vnet"
  address_space       = [var.vnet_address_space]
  location            = azurerm_resource_group.this.location
  resource_group_name = azurerm_resource_group.this.name
  tags                = var.tags
}

resource "azurerm_subnet" "aks" {
  name                 = "aks-subnet"
  resource_group_name  = azurerm_resource_group.this.name
  virtual_network_name = azurerm_virtual_network.this.name
  address_prefixes     = [var.aks_subnet_prefix]
}

resource "azurerm_network_security_group" "aks" {
  name                = "${var.resource_group_name}-aks-nsg"
  location            = azurerm_resource_group.this.location
  resource_group_name = azurerm_resource_group.this.name
  tags                = var.tags

  # Demo-only hole: the node pool has a public IP per node instead of a
  # Standard Load Balancer / NAT gateway (issue #26 scope update, to avoid
  # their hourly billing), so the gateway's NodePort has to be opened
  # straight from Internet at the NSG instead of via an LB frontend rule.
  # No TLS, no WAF — acceptable only because the cluster is created for one
  # demo and destroyed straight after (ARCHITECTURE.md §11.1 point 3).
  security_rule {
    name                       = "allow-gateway-nodeport"
    priority                   = 100
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = var.demo_inbound_port
    source_address_prefix      = "Internet"
    destination_address_prefix = "*"
  }

  # Caddy (infrastructure/kubernetes/overlays/azure/caddy-tls.yaml, ADR-019)
  # runs hostNetwork on the same node's static public IP and needs 80 for
  # its Let's Encrypt HTTP-01 challenge and 443 for the HTTPS it terminates
  # — same "demo hole, no LB/WAF" tradeoff as the NodePort rule above.
  security_rule {
    name                       = "allow-caddy-http-acme"
    priority                   = 110
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "80"
    source_address_prefix      = "Internet"
    destination_address_prefix = "*"
  }

  security_rule {
    name                       = "allow-caddy-https"
    priority                   = 111
    direction                  = "Inbound"
    access                     = "Allow"
    protocol                   = "Tcp"
    source_port_range          = "*"
    destination_port_range     = "443"
    source_address_prefix      = "Internet"
    destination_address_prefix = "*"
  }
}

resource "azurerm_subnet_network_security_group_association" "aks" {
  subnet_id                 = azurerm_subnet.aks.id
  network_security_group_id = azurerm_network_security_group.aks.id
}
