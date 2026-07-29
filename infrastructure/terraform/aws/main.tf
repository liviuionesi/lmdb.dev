# Zero-spend tripwire first (ARCHITECTURE.md §11.1 point 4 / issue #26
# scope update, applied the same way here) — the explicit depends_on below
# is what makes network wait on it despite not consuming any of its outputs.
module "budget_guard" {
  source = "../modules/budget-guard-aws"

  alert_emails  = var.alert_emails
  budget_amount = var.budget_amount
  tags          = var.tags
}

module "network" {
  source = "../modules/network-aws"

  vpc_cidr           = var.vpc_cidr
  public_subnet_cidr = var.public_subnet_cidr
  ssh_cidr           = var.ssh_cidr
  demo_inbound_port  = var.demo_inbound_port
  tags               = var.tags

  depends_on = [module.budget_guard]
}

module "cluster_k3s" {
  source = "../modules/cluster-k3s"

  instance_name     = var.instance_name
  instance_type     = var.instance_type
  root_volume_size  = var.root_volume_size
  subnet_id         = module.network.subnet_id
  security_group_id = module.network.security_group_id
  ssh_public_key    = var.ssh_public_key
  tags              = var.tags
}
