data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]
  }

  filter {
    name   = "architecture"
    values = ["x86_64"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "aws_key_pair" "k3s" {
  key_name   = "${var.instance_name}-key"
  public_key = var.ssh_public_key

  tags = var.tags
}

resource "aws_instance" "k3s_server" {
  ami                         = data.aws_ami.al2023.id
  instance_type               = var.instance_type
  subnet_id                   = var.subnet_id
  vpc_security_group_ids      = [var.security_group_id]
  key_name                    = aws_key_pair.k3s.key_name
  associate_public_ip_address = true

  # k3s bundles its own container runtime and a single-binary control plane +
  # kubelet — this is what makes a single t3.micro (1 vCPU/1GB) viable at
  # all; a full kubeadm cluster's control-plane components alone wouldn't
  # fit, and EKS's managed control plane isn't part of the free tier
  # (~$73/month) — ARCHITECTURE.md §11.1/§11.2, issue #27.
  # 1. Fetch the instance's own public IP via IMDSv2 (token-based — works
  #    regardless of the account's IMDSv1/v2 default).
  # 2. Disable Traefik: the gateway is reached directly on the node's public
  #    IP via NodePort (no LB/Ingress controller in front — bills hourly,
  #    out of scope for a $0 demo), so k3s's bundled ingress controller is
  #    dead weight on a 1GB node.
  # 3. --node-external-ip / --tls-san so the kubeconfig written on the box,
  #    and the cert it presents, already reference the public IP rather
  #    than the instance's private IP or localhost.
  # 4. write-kubeconfig-mode so the file is readable without sudo once
  #    fetched over SSH (see infrastructure/terraform/README.md).
  user_data = <<-EOF
    #!/bin/bash
    set -euo pipefail
    TOKEN=$(curl -s -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 21600")
    PUBLIC_IP=$(curl -s -H "X-aws-ec2-metadata-token: $TOKEN" http://169.254.169.254/latest/meta-data/public-ipv4)
    curl -sfL https://get.k3s.io | sh -s - \
      --disable traefik \
      --write-kubeconfig-mode 644 \
      --node-external-ip "$PUBLIC_IP" \
      --tls-san "$PUBLIC_IP"
  EOF

  root_block_device {
    volume_size = var.root_volume_size
    volume_type = "gp3"

    tags = merge(var.tags, { Name = "${var.instance_name}-root" })
  }

  tags = merge(var.tags, { Name = var.instance_name })
}
