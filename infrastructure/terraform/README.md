# Terraform — Azure AKS free-tier infrastructure

Provisions the primary cloud target (ARCHITECTURE.md §11.1–11.2, issue #26):
AKS with a free control plane + one free-tier-eligible B-series node, fronted
directly by the node's own public IP (no Standard Load Balancer, no NAT
gateway — both bill hourly). Images are pulled from ghcr.io, which is public,
so there's no registry/pull-secret to provision.

The AWS side (`aws/`, k3s on EC2 t3.micro) is issue #27 and isn't part of
this directory yet.

**Hard constraint: $0.** Read ARCHITECTURE.md §11.1 before touching this if
you haven't — it explains the reasoning behind every choice below (ephemeral
clusters, the budget-guard tripwire, why ACR/LB/NAT gateway are avoided).

## Layout

```
infrastructure/terraform/
├── modules/
│   ├── network/         # resource group, VNet, subnet, NSG (opens the demo NodePort)
│   ├── cluster-aks/      # AKS: Free sku_tier, 1 node, enable_node_public_ip
│   └── budget-guard/     # zero-spend subscription budget + email alert — applied FIRST
└── azure/                # composition: budget-guard → network → cluster-aks
```

## Prerequisites

- [Terraform](https://developer.hashicorp.com/terraform/install) >= 1.9
- [Azure CLI](https://learn.microsoft.com/cli/azure/install-azure-cli) (`az`)
- An Azure account on the **free plan with the default spending limit ON**
  (https://azure.microsoft.com/free/) — confirm that's still the actual
  no-invoice plan at signup time, terms change. Never upgrade it to
  pay-as-you-go.

## 1. Bootstrap remote state (one-time)

Terraform state is never committed. It lives in an Azure Storage account
that isn't part of the `azure/` composition itself (that would be circular —
you'd need state to create the thing that holds state), so create it once by
hand:

```bash
az login

export TF_STATE_RG=filmpire-tfstate
export TF_STATE_SA=filmpiretfstate$RANDOM   # must be globally unique, lowercase, no dashes, <=24 chars
export TF_STATE_CONTAINER=tfstate
export LOCATION=westeurope                  # match azure/variables.tf's `location`

az group create --name "$TF_STATE_RG" --location "$LOCATION" \
  --tags project=filmpire managed-by=manual-bootstrap

az storage account create \
  --name "$TF_STATE_SA" \
  --resource-group "$TF_STATE_RG" \
  --location "$LOCATION" \
  --sku Standard_LRS \
  --kind StorageV2 \
  --min-tls-version TLS1_2 \
  --allow-blob-public-access false \
  --tags project=filmpire managed-by=manual-bootstrap

az storage container create \
  --name "$TF_STATE_CONTAINER" \
  --account-name "$TF_STATE_SA" \
  --auth-mode login
```

Save the values (they're not secret, just account-specific, which is why
they're not hardcoded in `azure/backend.tf`):

```bash
cat > infrastructure/terraform/azure/backend.hcl <<EOF
resource_group_name  = "$TF_STATE_RG"
storage_account_name = "$TF_STATE_SA"
container_name        = "$TF_STATE_CONTAINER"
EOF
```

`backend.hcl` is gitignored — re-create it locally (or from your password
manager) rather than committing it.

## 2. Credentials

**Local/manual apply (recommended to start):** just `az login`. The azurerm
provider automatically uses your Azure CLI session when no `ARM_CLIENT_ID`
is set — no service principal needed for a one-person demo workflow.

**CI (`workflow_dispatch` only, per ARCHITECTURE.md §11.4 — plan can run on
PRs, apply never runs automatically):**

```bash
az ad sp create-for-rbac --name filmpire-terraform \
  --role Contributor --scopes "/subscriptions/$(az account show --query id -o tsv)"
```

Map the output to `ARM_CLIENT_ID` / `ARM_CLIENT_SECRET` / `ARM_TENANT_ID`,
plus `ARM_SUBSCRIPTION_ID` from `az account show --query id -o tsv`. Store
these as GitHub Actions repo secrets — never in `.tf` files or `tfvars`.

## 3. Configure and apply

```bash
cd infrastructure/terraform/azure
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars`: set `alert_emails` and `budget_start_date` (must be
the first of the current month — `date -u +%Y-%m-01T00:00:00Z`). Both are
required with no default on purpose; see `modules/budget-guard/variables.tf`.

```bash
terraform init -backend-config=backend.hcl
terraform plan
terraform apply    # ~15 min (ARCHITECTURE.md §11.1 point 3) — mostly AKS provisioning
```

`node_public_ip` (and therefore `gateway_url`) can come back `null`
immediately after apply if the node's public IP hadn't finished attaching
when Terraform read it. If so: `terraform refresh && terraform output gateway_url`.

## 4. Deploy the app and verify

```bash
az aks get-credentials \
  --resource-group "$(terraform output -raw resource_group_name)" \
  --name "$(terraform output -raw cluster_name)" \
  --overwrite-existing

kubectl apply -k ../../kubernetes/overlays/azure
kubectl get pods -w   # wait for Running/Ready

curl "$(terraform output -raw gateway_url)/actuator/health"
```

`overlays/azure` patches the gateway's Service to `NodePort` 30080 (see its
`kustomization.yaml`) so it lands on the same port `modules/network` opened
in the NSG. If you change `demo_inbound_port` in `terraform.tfvars`, update
that patch to match.

## 5. Tear down

```bash
terraform destroy
```

This removes the cluster (and everything Kubernetes created inside it —
there's nothing outside the AKS-managed node resource group to clean up
separately). Do this the same session as the demo; nothing in this
composition is meant to run unattended.

## Cost notes (be honest with yourself here)

- AKS control plane: free (`sku_tier = "Free"`).
- `Standard_B2ats_v2` node: free-tier eligible, 750 h/month for 12 months.
- The budget-guard module applies a $1 zero-spend tripwire on the whole
  subscription **before** anything else, and emails `alert_emails` the same
  day if actual or forecasted spend goes positive.
- **Not literally $0 while the cluster is up:** `enable_node_public_ip`
  attaches a Standard Public IP to the node, which bills at Azure's
  standard per-hour rate for that SKU (a few cents for a demo-length
  session, not free). This is the deliberate trade for avoiding a Standard
  Load Balancer/NAT gateway, which would cost more and add setup
  complexity disproportionate to a demo. Destroy promptly after each demo.
- AKS still provisions its own Standard Load Balancer internally (Azure
  retired the Basic SKU option for new clusters) purely for outbound SNAT.
  We never create a Kubernetes `Service: type=LoadBalancer` or an
  `azurerm_lb`/`azurerm_nat_gateway` resource ourselves, and with zero
  inbound rules that LB carries no meaningful charge — see the comment in
  `modules/cluster-aks/main.tf` if you want the full reasoning.

## What this doesn't cover yet

- The actual `apply` → `kubectl apply -k` → `destroy` round-trip has **not
  been run against a real Azure subscription** — this session had neither
  Azure credentials nor an existing account to test against. `terraform
  fmt`/`validate` pass locally (see below) but a live apply can still
  surface things static validation can't, e.g. region capacity for
  `Standard_B2ats_v2`, or subscription-level policy restrictions.
- `terraform plan` in CI on PRs (checklist item in #26) — not added. This
  repo's convention (CLAUDE.md) is commit-straight-to-`main`, no PRs, and a
  standing rule not to touch CI/CD workflows without asking. Flagged for the
  user rather than added unilaterally.
- `aws/` (issue #27) is a separate task.
