variable "instance_name" {
  description = "Name tag for the EC2 instance and its key pair."
  type        = string
  default     = "filmpire-k3s"
}

variable "instance_type" {
  description = <<-EOT
    EC2 instance type for the single k3s node. t3.micro (1 vCPU/1GB,
    AWS's free-tier-eligible size, ARCHITECTURE.md §11.1) was the original
    target, but a live apply (#27, 2026-08-01) found it OOM-thrashes under
    this app's real footprint — not a tight-but-workable squeeze, a
    genuine crash-inducing shortage (see infrastructure/terraform/README.md's
    "Lessons from the first live run"). t3.medium is blocked outright on
    this account (FreeTierRestrictionError). t3.small (2 vCPU/2GB) is the
    smallest size that actually stays up — a small-but-real hourly cost,
    not free-tier; the budget-guard tripwire is the actual cost control.
  EOT
  type        = string
  default     = "t3.small"
}

variable "root_volume_size" {
  description = "Root EBS volume size in GB. 30GB is the al2023 AMI's minimum root snapshot size and also the full 30GB/month free-tier EBS allowance (ARCHITECTURE.md §11.1) — there's no smaller value that still boots this AMI."
  type        = number
  default     = 30
}

variable "subnet_id" {
  description = "Subnet (from modules/network-aws) the instance attaches to."
  type        = string
}

variable "security_group_id" {
  description = "Security group (from modules/network-aws) opening SSH + the demo NodePort."
  type        = string
}

variable "ssh_public_key" {
  description = "Public key content (e.g. `cat ~/.ssh/id_ed25519.pub`) installed on the node for kubeconfig retrieval and debugging. No default — supply via terraform.tfvars."
  type        = string
}

variable "tags" {
  description = "Tags applied to every resource this module creates."
  type        = map(string)
  default     = {}
}
