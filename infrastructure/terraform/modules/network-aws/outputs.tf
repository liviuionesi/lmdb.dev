output "vpc_id" {
  value = aws_vpc.this.id
}

output "subnet_id" {
  description = "Public subnet the k3s node attaches to — consumed by cluster-k3s."
  value       = aws_subnet.public.id
}

output "security_group_id" {
  description = "Security group opening SSH + the demo NodePort — consumed by cluster-k3s."
  value       = aws_security_group.k3s.id
}
