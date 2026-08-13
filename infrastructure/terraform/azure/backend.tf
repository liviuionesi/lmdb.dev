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
  # use_azuread_auth: state access (read/lock) goes through the caller's
  # Entra ID identity — your `az login` locally, the GitHub Actions OIDC
  # identity in CI — instead of a Storage Account access key. Keys are
  # long-lived secrets; this avoids needing one at all. Requires the
  # caller to hold a data-plane role (Storage Blob Data Contributor) on
  # the storage account, not just the management-plane access `az login`
  # already implies — see README.md.
  backend "azurerm" {
    key              = "lmdb-azure.tfstate"
    use_azuread_auth = true
  }
}

provider "azurerm" {
  features {}

  # Default ("legacy") behavior tries to auto-register EVERY Resource
  # Provider azurerm knows about — including ones this config never
  # touches. Hit this for real on a fresh subscription: registration of
  # the irrelevant Microsoft.DataMigration timed out and failed the whole
  # plan. Scope registration to exactly what modules/{network,cluster-aks,
  # budget-guard} actually use.
  resource_provider_registrations = "none"
  resource_providers_to_register = [
    "Microsoft.Network",
    "Microsoft.Compute",
    "Microsoft.ContainerService",
    "Microsoft.Consumption",
    "Microsoft.ManagedIdentity",
  ]
}
