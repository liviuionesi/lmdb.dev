resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = merge(var.tags, { Name = "filmpire-vpc" })
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = merge(var.tags, { Name = "filmpire-igw" })
}

# A single AZ is enough for a one-node demo cluster — no HA requirement, and
# picking the account's first available AZ avoids hardcoding one that might
# not exist for a given account/region (same "don't assume, check" lesson as
# modules/cluster-aks's region note).
data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.this.id
  cidr_block              = var.public_subnet_cidr
  availability_zone       = data.aws_availability_zones.available.names[0]
  map_public_ip_on_launch = true

  tags = merge(var.tags, { Name = "filmpire-public-subnet" })
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }

  tags = merge(var.tags, { Name = "filmpire-public-rt" })
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

# Demo-only inbound rules (ARCHITECTURE.md §11.1 point 5 / issue #26 scope
# update, same reasoning applied here): no Elastic Load Balancer in front of
# the node (bills hourly), so the gateway is reached directly on the
# instance's public IP via NodePort, and that NodePort has to be opened
# straight from the Internet here instead of at an LB frontend rule. SSH is
# open the same way, for kubeconfig retrieval (no AWS-managed equivalent of
# `az aks get-credentials` for a self-managed k3s node). Acceptable only
# because the instance is created for one demo and destroyed straight after.
resource "aws_security_group" "k3s" {
  name        = "filmpire-k3s-sg"
  description = "Inbound SSH + gateway NodePort for the single k3s demo node (issue #27)."
  vpc_id      = aws_vpc.this.id

  ingress {
    description = "SSH for kubeconfig retrieval / debugging"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.ssh_cidr]
  }

  ingress {
    description = "Gateway NodePort, reached directly on the node's public IP"
    from_port   = var.demo_inbound_port
    to_port     = var.demo_inbound_port
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Unrestricted outbound (k3s pulling images from ghcr.io, get.k3s.io install script, etc.)"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = merge(var.tags, { Name = "filmpire-k3s-sg" })
}
