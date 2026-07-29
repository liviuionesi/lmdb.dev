terraform {
  required_version = ">= 1.9"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }

  # Remote state: S3 bucket + DynamoDB lock table (ARCHITECTURE.md §11.2).
  # Only `key` is set here — bucket/region/dynamodb_table are supplied at
  # `terraform init` time via -backend-config so this file stays portable
  # and no account-specific state-storage details are committed. See
  # ../README.md for the bootstrap steps and the exact init command.
  backend "s3" {
    key     = "filmpire-aws.tfstate"
    encrypt = true
  }
}

provider "aws" {
  region = var.region

  default_tags {
    tags = var.tags
  }
}
