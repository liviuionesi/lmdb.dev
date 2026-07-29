# Terraform — Azure AKS free-tier infrastructure

Provisions the primary cloud target (ARCHITECTURE.md §11.1–11.2, issue #26):
AKS with a free control plane + one node, fronted directly by the node's own
public IP (no Standard Load Balancer, no NAT gateway — both bill hourly).
Images are pulled from ghcr.io, which is public, so there's no
registry/pull-secret to provision.

**Live-tested 2026-07-29:** a full `apply` → `kubectl apply -k` →
`destroy` round-trip was actually run against a real Azure subscription,
not just planned. It surfaced real things static `validate` can't catch —
see "Lessons from the first live run" below before you assume anything in
this doc is still exactly right. Re-verify against your own subscription;
several of these are subscription/region-specific and can change.

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
│   ├── cluster-aks/      # AKS: Free sku_tier, 1 node, node_public_ip_enabled
│   └── budget-guard/     # zero-spend subscription budget + email alert — applied FIRST
└── azure/                # composition: budget-guard → network → cluster-aks
```

## Prerequisites

- [Terraform](https://developer.hashicorp.com/terraform/install) >= 1.9
- [Azure CLI](https://learn.microsoft.com/cli/azure/install-azure-cli) (`az`)
  — on Fedora, `sudo dnf install -y azure-cli` after adding Microsoft's repo
  (see Microsoft's install docs); worked cleanly on Fedora 44 despite that
  not being an officially-listed distro.
- An Azure account on the **free plan with the default spending limit ON**
  (https://azure.microsoft.com/free/) — confirm that's still the actual
  no-invoice plan at signup time, terms change. Never upgrade it to
  pay-as-you-go. Signing in with `az login` via a GitHub-federated Microsoft
  account authenticates you but does **not** by itself create a
  subscription — `az account show` will say "No subscriptions found" until
  you separately complete the free-account signup flow.

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
export LOCATION=eastus                      # see the region note below — this is NOT a safe assumption

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

**Region is not a safe assumption — check it live.** On the subscription
this was tested against, `westeurope` returned `RequestDisallowedByAzure:
The selected region is currently not accepting new customers` on the very
first resource (a storage account) — a dynamic, subscription-specific
restriction, not a config error. `eastus` worked. If a region rejects you,
just try another; there's no way to know in advance which ones are open to
a given subscription.

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

**Local/manual apply:** just `az login`. The azurerm provider automatically
uses your Azure CLI session when no `ARM_CLIENT_ID` is set — no service
principal needed for a one-person demo workflow. State access also goes
through your signed-in identity (`use_azuread_auth = true` on the backend —
see below), so you additionally need the **Storage Blob Data Contributor**
role on the tfstate storage account, not just management-plane access:

```bash
az role assignment create \
  --assignee "$(az ad signed-in-user show --query id -o tsv)" \
  --role "Storage Blob Data Contributor" \
  --scope "/subscriptions/$(az account show --query id -o tsv)/resourceGroups/$TF_STATE_RG/providers/Microsoft.Storage/storageAccounts/$TF_STATE_SA"
```

Role assignments can take a minute or two to actually take effect even
after the CLI call returns success — if `terraform init`/`plan` fails with
`AuthorizationPermissionMismatch` right after creating this, just retry
after a short wait rather than assuming something's wrong.

**CI — GitHub OIDC, not a stored secret (what's actually wired up):** an
Azure AD App Registration trusts GitHub's OIDC issuer for
`repo:pehlivanu/filmpire-microservices:ref:refs/heads/main` specifically —
only workflow runs from a push to `main` in this exact repo can mint a
token as this identity. No `ARM_CLIENT_SECRET` exists anywhere; nothing to
rotate or leak. Set up once:

```bash
APP_ID=$(az ad app create --display-name filmpire-github-actions --query appId -o tsv)
az ad sp create --id "$APP_ID"

az ad app federated-credential create --id "$APP_ID" --parameters '{
  "name": "filmpire-main-push",
  "issuer": "https://token.actions.githubusercontent.com",
  "subject": "repo:pehlivanu/filmpire-microservices:ref:refs/heads/main",
  "audiences": ["api://AzureADTokenExchange"]
}'

# Least privilege: Reader on the subscription (enough to compute a plan),
# Storage Blob Data Contributor scoped ONLY to the tfstate account (enough
# to read/lock state). This identity cannot create, modify, or delete any
# real infrastructure — it can never run `apply`.
az role assignment create --assignee "$APP_ID" --role Reader \
  --scope "/subscriptions/$(az account show --query id -o tsv)"
az role assignment create --assignee "$APP_ID" --role "Storage Blob Data Contributor" \
  --scope "/subscriptions/$(az account show --query id -o tsv)/resourceGroups/$TF_STATE_RG/providers/Microsoft.Storage/storageAccounts/$TF_STATE_SA"
```

Then set these as **GitHub Actions repo variables** (`gh variable set NAME
--body value`, or Settings → Secrets and variables → Actions → Variables —
not Secrets, none of these are sensitive on their own without the live
OIDC trust above): `AZURE_CLIENT_ID` (the `$APP_ID`), `AZURE_TENANT_ID`,
`AZURE_SUBSCRIPTION_ID`, `TF_STATE_RESOURCE_GROUP`,
`TF_STATE_STORAGE_ACCOUNT`, `TF_STATE_CONTAINER`, `ALERT_EMAIL`. See
`.github/workflows/terraform-plan.yml` for how they're consumed — it runs
`terraform plan` (never `apply`) on every push to `main` touching this
directory. Not on PRs: this repo commits straight to `main` with no PR
workflow, so `pull_request` would be a trigger that never fires.

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
terraform plan -out=tfplan
terraform apply tfplan    # a few minutes, mostly AKS provisioning
```

## 4. Deploy the app and verify

```bash
az aks get-credentials \
  --resource-group "$(terraform output -raw resource_group_name)" \
  --name "$(terraform output -raw cluster_name)" \
  --overwrite-existing

kubectl apply -k ../../kubernetes/overlays/azure
kubectl get pods -w   # wait for Running/Ready

# Terraform deliberately does NOT output the node's public IP — see
# "Lessons from the first live run" below for why. Get it from kubectl:
kubectl get nodes -o wide   # NODE's EXTERNAL-IP column is the real one

curl "http://<EXTERNAL-IP>:$(terraform output -raw demo_inbound_port)/actuator/health"
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

## Lessons from the first live run (2026-07-29)

Static `validate`/`plan` can't catch these — all four only surfaced on a
real `apply` against a real subscription:

1. **AKS enforces its own node minimum, independent of "free-tier
   eligible."** `Standard_B2ats_v2` (this doc's original example, 2
   vCPU/1GB) failed with `SystemPoolSkuTooLow` — AKS requires ≥2 vCPU
   **and** ≥4GB memory for whatever SKU runs the system pool.
2. **A whole VM family can be blocked for a subscription, in a specific
   region, independent of AKS's rules.** `Standard_B2s` (2 vCPU/4GB, clears
   AKS's minimum) was rejected outright: *"The VM size of Standard_B2s is
   not allowed in your subscription in location 'eastus'."* The entire
   B-series was absent from the allowed-SKU list Azure returned — brand-new
   free-trial subscriptions can have burstable VMs blocked region-wide as
   an anti-abuse measure. Check `az vm list-skus -l <region>` for what's
   actually allowed before picking a size.
3. **VM family naming conventions aren't reliable across generations.**
   Guessed `Standard_F2as_v7` (compute-optimized "should be" 2 vCPU/4GB by
   older F-series convention) — it's actually 2 vCPU/**8GB** on this
   generation. `Standard_D2ls_v7` (explicitly named "low memory" D-variant)
   turned out to be the one that's actually 2 vCPU/4GB. Check real specs
   with `az vm list-skus`, don't infer from the name.
4. **`terraform output`-ing the node's public IP is a trap.** The obvious
   approach (`data.azurerm_public_ips` against the AKS node resource group)
   compiles, applies cleanly, and returns a real, valid-looking IP — that
   happens to be the AKS-managed outbound Load Balancer's address, not the
   node's. NodePort traffic to that IP goes nowhere. The actual reachable
   address (from `node_public_ip_enabled`) is a **VMSS instance-level**
   public IP, a resource kind that data source can't see at all — only
   `az vmss list-instance-public-ips` or `kubectl get nodes -o wide` finds
   it. `modules/cluster-aks` deliberately has no `node_public_ip` output
   because of this — see the comment in its `main.tf`.

Also found live, not yet fixed (out of scope for this issue, noted for
whoever picks them up): `api-gateway`/`movie-service` can't actually start
on a fresh apply — their images (`ghcr.io/pehlivanu/filmpire-*:latest`)
have never been published, since #28 (CI/CD image publish) doesn't exist
yet. `mongodb` also crash-looped for a separate, not-yet-diagnosed reason
(exits cleanly — `lastState.terminated.reason: Completed`, not
`OOMKilled` — so it's not simply the tight resource limits; worth a closer
look whenever #28 unblocks the rest of the stack enough to test it
properly). `redis` came up healthy with no issues.

## Cost notes (be honest with yourself here)

- AKS control plane: free (`sku_tier = "Free"`).
- The node is **not free-tier in the strict sense** — see lesson 1 and 2
  above. Whatever `vm_size` ends up working on your subscription/region is
  probably a small-but-nonzero hourly cost, not a guaranteed-free one.
  Check `modules/cluster-aks/variables.tf`'s current default and its
  description for the specific reasoning behind whatever's there now.
- The budget-guard module applies a $1 zero-spend tripwire on the whole
  subscription **before** anything else, and emails `alert_emails` the same
  day if actual or forecasted spend goes positive. This — not any specific
  SKU's "free-tier" label — is the actual cost control.
- `enable_node_public_ip` attaches a Standard Public IP to the node, which
  bills at Azure's standard per-hour rate for that SKU (a few cents for a
  demo-length session, not free). This is the deliberate trade for avoiding
  a Standard Load Balancer/NAT gateway, which would cost more and add setup
  complexity disproportionate to a demo. Destroy promptly after each demo.
- AKS still provisions its own Standard Load Balancer internally (Azure
  retired the Basic SKU option for new clusters) purely for outbound SNAT.
  We never create a Kubernetes `Service: type=LoadBalancer` or an
  `azurerm_lb`/`azurerm_nat_gateway` resource ourselves, and with zero
  inbound rules that LB carries no meaningful charge — see the comment in
  `modules/cluster-aks/main.tf` if you want the full reasoning.

## What this doesn't cover yet

- The app itself isn't reachable end-to-end yet — blocked on #28 (CI/CD
  image publish); see "Lessons from the first live run" above. The
  infrastructure side (cluster, network, NSG, budget-guard) is confirmed
  working independent of that.
- The `mongodb` crash-loop noted above isn't root-caused.
- `aws/` (issue #27) is a separate task.
