variable "cluster_name" {
  description = "AKS cluster name."
  type        = string
  default     = "lmdb-aks"
}

variable "location" {
  type = string
}

variable "resource_group_name" {
  description = "Resource group to create the cluster in — pass through module.network's output so networking and cluster share one group."
  type        = string
}

variable "subnet_id" {
  description = "Subnet (from modules/network) the default node pool attaches to."
  type        = string
}

variable "vm_size" {
  description = <<-EOT
    Node size for the single-node system pool. NOT free-tier B-series —
    two real findings from a live apply on 2026-07-29 ruled that whole
    plan out, in order:
    (1) Standard_B2ats_v2 (ARCHITECTURE.md's original example): AKS
        rejects it for the system pool with "SystemPoolSkuTooLow" — Azure
        enforces a hard minimum of 2 vCPU AND 4GB memory for whatever SKU
        runs the system pool, and B2ats_v2's 1GB RAM falls short despite
        being labeled free-tier eligible.
    (2) Standard_B2s (2 vCPU/4GB, clears that minimum): rejected outright
        by this subscription — "The VM size of Standard_B2s is not
        allowed in your subscription in location 'eastus'". The entire
        B-series family is absent from the allowed-SKU list Azure
        returned; brand-new free-trial subscriptions can have burstable
        VMs blocked entirely in a given region (anti-abuse — B-series is
        the family crypto-mining scripts target), which isn't something
        `terraform plan` can see coming since it's a subscription-level
        allow-list, not a config error.
    Checked actual specs with `az vm list-skus` rather than assume from
    naming convention alone — good thing: Standard_F2as_v7 (the
    "compute-optimized, should be lean on memory" guess) is actually 2
    vCPU/8GB on this generation, not the 2GB-per-vCPU ratio older F-series
    generations had. Standard_D2ls_v7 (the explicitly-named "low memory" D
    variant) IS 2 vCPU/4GB exactly — confirmed present in the same
    allowed-SKU list — so that's the pick: smallest size that both clears
    AKS's minimum and is actually allowed. This is no longer "free-tier"
    in the strict sense — small nonzero cost per hour, same reasoning as
    enable_node_public_ip below. budget-guard is the actual backstop;
    re-verify both the allowed-SKU list AND actual specs if this ever
    needs picking again — neither is a fixed fact, and naming conventions
    across VM generations aren't reliable enough to skip checking.
  EOT
  type        = string
  default     = "Standard_D2ls_v7"
}

variable "node_count" {
  description = "Node count for the default pool. One node only — this is a demo cluster, not HA."
  type        = number
  default     = 1
}

variable "enable_node_public_ip" {
  description = <<-EOT
    Gives each node its own public IP so the gateway is reachable on its
    NodePort without a Standard Load Balancer or NAT gateway (issue #26
    scope update). Note this IS a small billable resource (~$0.005/hr per
    Azure's Standard Public IP pricing) — not literally $0 while the
    cluster is up, but the ephemeral apply→demo→destroy cycle keeps total
    exposure to a few cents. See infrastructure/terraform/README.md.
  EOT
  type        = bool
  default     = true
}

variable "demo_inbound_port" {
  description = "NodePort the gateway is exposed on. Needed here (not just modules/network's subnet-level NSG) because AKS auto-creates a SECOND, NIC-level NSG on the node's VMSS when enable_node_public_ip is true — see the security rule below."
  type        = number
  default     = 30080
}

variable "tags" {
  type    = map(string)
  default = {}
}
