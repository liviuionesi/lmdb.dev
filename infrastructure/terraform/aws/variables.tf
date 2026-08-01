variable "region" {
  description = "AWS region. us-east-1 is the safe default for free-tier account age/availability; re-verify against your own account if it ever rejects a resource the way westeurope did for Azure (see ../azure/variables.tf's location note) — no region is a permanently safe assumption."
  type        = string
  default     = "us-east-1"
}

variable "vpc_cidr" {
  type    = string
  default = "10.20.0.0/16"
}

variable "public_subnet_cidr" {
  type    = string
  default = "10.20.1.0/24"
}

variable "ssh_cidr" {
  description = "CIDR allowed to SSH into the k3s node. See modules/network-aws/variables.tf for the full reasoning."
  type        = string
  default     = "0.0.0.0/0"
}

variable "demo_inbound_port" {
  description = "NodePort the gateway is exposed on; must match the Service patch in infrastructure/kubernetes/overlays/aws."
  type        = number
  default     = 30080
}

variable "instance_name" {
  type    = string
  default = "filmpire-k3s"
}

variable "instance_type" {
  description = "See modules/cluster-k3s/variables.tf — t3.micro OOM-thrashes under this app's real footprint (found live, #27); t3.small is the smallest size that actually stays up."
  type        = string
  default     = "t3.small"
}

variable "root_volume_size" {
  type    = number
  default = 30
}

variable "ssh_public_key" {
  description = "Public key content installed on the k3s node (e.g. `cat ~/.ssh/id_ed25519.pub`). No default — a real key, supply via terraform.tfvars."
  type        = string
}

variable "alert_emails" {
  description = "Who gets the zero-spend budget alert. No default — this is a real email, supply it via terraform.tfvars (see README.md)."
  type        = list(string)
}

variable "budget_amount" {
  description = "Zero-spend tripwire threshold in USD."
  type        = number
  default     = 1
}

variable "tags" {
  type = map(string)
  default = {
    project     = "filmpire"
    managed-by  = "terraform"
    environment = "demo"
  }
}
