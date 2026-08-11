output "instance_id" {
  description = "The EC2 instance ID running k3s."
  value       = module.cluster_k3s.instance_id
}

output "public_ip" {
  description = "The k3s node's public IP — reachable directly (see modules/cluster-k3s), unlike the AKS case."
  value       = module.cluster_k3s.public_ip
}

output "ssh_user" {
  value = module.cluster_k3s.ssh_user
}

output "demo_inbound_port" {
  value = var.demo_inbound_port
}

