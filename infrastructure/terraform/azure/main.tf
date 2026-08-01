# Zero-spend tripwire first (ARCHITECTURE.md §11.1 point 4 / issue #26
# scope update) — the explicit depends_on below is what makes the other
# modules wait on it despite not consuming any of its outputs.
module "budget_guard" {
  source = "../modules/budget-guard"

  alert_emails      = var.alert_emails
  budget_amount     = var.budget_amount
  budget_start_date = var.budget_start_date
}

module "network" {
  source = "../modules/network"

  resource_group_name = var.resource_group_name
  location            = var.location
  demo_inbound_port   = var.demo_inbound_port
  tags                = var.tags

  depends_on = [module.budget_guard]
}

module "cluster_aks" {
  source = "../modules/cluster-aks"

  cluster_name          = var.cluster_name
  location              = module.network.location
  resource_group_name   = module.network.resource_group_name
  subnet_id             = module.network.aks_subnet_id
  vm_size               = var.vm_size
  node_count            = var.node_count
  enable_node_public_ip = var.enable_node_public_ip
  demo_inbound_port     = var.demo_inbound_port
  tags                  = var.tags
}
