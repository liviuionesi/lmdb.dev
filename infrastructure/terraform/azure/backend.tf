terraform {
  required_version = ">= 1.9"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 4.0"
    }
  }

  # Remote state: Azure Storage account (ARCHITECTURE.md §11.2). Only `key`
  # is set here — resource_group_name/storage_account_name/container_name
  # are supplied at `terraform init` time via -backend-config so this file
  # stays portable and no account-specific state-storage details are
  # committed. See ../README.md for the bootstrap steps and the exact init
  # command.
  backend "azurerm" {
    key = "filmpire-azure.tfstate"
  }
}

provider "azurerm" {
  features {}
}
