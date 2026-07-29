variable "vpc_cidr" {
  description = "CIDR for the VPC."
  type        = string
  default     = "10.20.0.0/16"
}

variable "public_subnet_cidr" {
  description = "CIDR for the public subnet the k3s node attaches to, carved out of vpc_cidr."
  type        = string
  default     = "10.20.1.0/24"
}

variable "ssh_cidr" {
  description = <<-EOT
    CIDR allowed to SSH into the k3s node (needed to fetch /etc/rancher/k3s/k3s.yaml
    — there's no AWS-managed equivalent of `az aks get-credentials` for a
    self-managed node). Defaults to 0.0.0.0/0 for demo convenience
    (ARCHITECTURE.md §11.1: ephemeral apply→demo→destroy, nothing runs
    unattended) — narrow this to your own IP/32 in terraform.tfvars if you
    want less exposure during the demo window.
  EOT
  type        = string
  default     = "0.0.0.0/0"
}

variable "demo_inbound_port" {
  description = "NodePort the gateway is exposed on; must match the Service patch in infrastructure/kubernetes/overlays/aws."
  type        = number
  default     = 30080
}

variable "tags" {
  description = "Tags applied to every resource this module creates."
  type        = map(string)
  default     = {}
}
