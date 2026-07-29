output "instance_id" {
  value = aws_instance.k3s_server.id
}

output "public_ip" {
  description = <<-EOT
    The node's public IP. Unlike the AKS case (modules/cluster-aks
    deliberately has no such output — a live apply found the natural
    Terraform-side approach, data.azurerm_public_ips, silently returns the
    wrong resource's IP), a plain EC2 instance's public IP is a normal,
    directly-queryable resource attribute — no equivalent trap here, so
    this output is safe to use directly (see infrastructure/terraform/README.md).
  EOT
  value       = aws_instance.k3s_server.public_ip
}

output "ssh_user" {
  description = "Default SSH user for Amazon Linux 2023 AMIs."
  value       = "ec2-user"
}
