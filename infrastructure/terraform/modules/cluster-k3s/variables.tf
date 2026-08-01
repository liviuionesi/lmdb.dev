variable "instance_name" {
  description = "Name tag for the EC2 instance and its key pair."
  type        = string
  default     = "filmpire-k3s"
}

variable "instance_type" {
  description = <<-EOT
    EC2 instance type for the single k3s node. t3.micro is the AWS
    free-tier-eligible size for the first 12 months (750 h/month,
    ARCHITECTURE.md §11.1). Unlike AKS's system-pool minimum (see
    modules/cluster-aks — 2 vCPU/4GB hard floor enforced by the platform),
    there's no equivalent platform-enforced floor here: k3s runs as a
    normal process on a normal instance, so t3.micro's 1 vCPU/1GB is tight
    but workable specifically because it's k3s (single binary) and not a
    full kubeadm control plane sharing the node.
  EOT
  type        = string
  default     = "t3.micro"
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
