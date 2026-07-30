# Terraform — cloud free-tier infrastructure

Provisions the two cloud targets (ARCHITECTURE.md §11.1–11.2): Azure AKS
(primary, issue #26) and AWS k3s-on-EC2 (secondary, issue #27). Both are
fronted directly by the node's own public IP (no Standard Load
Balancer/ELB, no NAT gateway — all bill hourly). Images are pulled from
ghcr.io, which is public, so there's no registry/pull-secret to provision
on either cloud.

**Hard constraint: $0.** Read ARCHITECTURE.md §11.1 before touching this if
you haven't — it explains the reasoning behind every choice below (ephemeral
clusters, the budget-guard tripwire, why ACR/ECR/LB/NAT gateway are
avoided).

## Layout

```
infrastructure/terraform/
├── modules/
│   ├── network/            # Azure: resource group, VNet, subnet, NSG (opens the demo NodePort)
│   ├── cluster-aks/         # Azure: AKS, Free sku_tier, 1 node, node_public_ip_enabled
│   ├── budget-guard/        # Azure: zero-spend subscription budget + email alert — applied FIRST
│   ├── network-aws/         # AWS: VPC, public subnet, security group (opens SSH + the demo NodePort)
│   ├── cluster-k3s/         # AWS: EC2 t3.micro + k3s bootstrap (user_data)
│   └── budget-guard-aws/    # AWS: zero-spend Budgets alert — applied FIRST
├── azure/                   # composition: budget-guard → network → cluster-aks
└── aws/                     # composition: budget-guard-aws → network-aws → cluster-k3s
```

Azure and AWS each get their own module set rather than sharing directory
names — `azurerm_consumption_budget_subscription` and `aws_budgets_budget`
(same for network/VPC resources) are different resource types entirely, so
there's no meaningful code to share between a `budget-guard` that works for
both providers.

## Prerequisites

- [Terraform](https://developer.hashicorp.com/terraform/install) >= 1.9

**Azure:**
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

**AWS:**
- [AWS CLI v2](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html)
  — on Fedora, `sudo dnf install -y awscli2`.
- An AWS account on the **free-tier plan** (https://aws.amazon.com/free/).
  Note AWS's post-July-2025 free tier for new accounts is credits-based
  (expires rather than converting to pay-as-you-go) — confirm the current
  terms at signup, they change. Never enable pay-as-you-go beyond the
  credits. Because the model is credit-based rather than a hard
  never-bills guarantee like Azure's spending-limit account, the ephemeral
  apply→demo→destroy pattern (below) and the budget-guard tripwire are
  what actually keep this at $0-equivalent, not the account type alone.
- An SSH key pair (`ssh-keygen -t ed25519` if you don't have one) — its
  public key gets installed on the k3s node for kubeconfig retrieval; there
  is no AWS equivalent of `az aks get-credentials` for a self-managed node.

---

## Azure (AKS)

**Live-tested 2026-07-29:** a full `apply` → `kubectl apply -k` →
`destroy` round-trip was actually run against a real Azure subscription,
not just planned. It surfaced real things static `validate` can't catch —
see "Lessons from the first live run" below before you assume anything in
this section is still exactly right. Re-verify against your own
subscription; several of these are subscription/region-specific and can
change.

### 1. Bootstrap remote state (one-time)

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

### 2. Credentials

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

### 3. Configure and apply

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

### 4. Deploy the app and verify

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

### 5. Tear down

```bash
terraform destroy
```

This removes the cluster (and everything Kubernetes created inside it —
there's nothing outside the AKS-managed node resource group to clean up
separately). Do this the same session as the demo; nothing in this
composition is meant to run unattended.

### Lessons from the first live run (2026-07-29)

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

Also found live, not yet fixed (out of scope for that issue, noted for
whoever picks them up): `api-gateway`/`movie-service` can't actually start
on a fresh apply — their images (`ghcr.io/pehlivanu/filmpire-*:latest`)
have never been published, since #28 (CI/CD image publish) doesn't exist
yet. `mongodb` also crash-looped for a separate, not-yet-diagnosed reason
(exits cleanly — `lastState.terminated.reason: Completed`, not
`OOMKilled` — so it's not simply the tight resource limits; worth a closer
look whenever #28 unblocks the rest of the stack enough to test it
properly). `redis` came up healthy with no issues.

### Cost notes (be honest with yourself here)

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

### What this doesn't cover yet

- The app itself isn't reachable end-to-end yet — blocked on #28 (CI/CD
  image publish); see "Lessons from the first live run" above. The
  infrastructure side (cluster, network, NSG, budget-guard) is confirmed
  working independent of that.
- The `mongodb` crash-loop noted above isn't root-caused.

---

## AWS (k3s on EC2)

Single `t3.micro` running [k3s](https://k3s.io/) (Traefik disabled, gateway
reached directly on the node's public IP via NodePort — same reasoning as
Azure's NSG rule). No EKS: its managed control plane is not part of the
free tier (~$73/month), and k3s's single-binary control plane + kubelet is
what makes a 1 vCPU/1GB instance viable at all. No ECR: images come from
ghcr.io (public), same as Azure — the issue's original `modules/registry`
(ECR) plan was replaced with `modules/budget-guard-aws` per the issue #27
scope-update comment, applied first for the same reason Azure's is.

**Untested memory-sizing risk, found by static review, not a live run —
watch for this specifically on the first apply.** `overlays/aws`'s core
slice (gateway + movie-service + MongoDB + Redis) sets container memory
*limits* that sum to 952Mi on a `t3.micro`'s 1024MiB total RAM — after k3s's
own control-plane + kubelet overhead, that's little to no headroom if
several containers approach their limit at once, and a plausible OOM-kill
risk under any real load. Left as-is deliberately rather than guessed-and-
trimmed now: Azure's actual working node size only emerged from a live
apply, not from tightening numbers on paper (see "Lessons from the first
live run" above) — same principle applies here. If the app OOM-kills on
first boot, this is the first thing to check; `kubectl top pods` and
`kubectl describe node` will show which container is actually the pressure
point before you guess at new numbers.

### 1. Bootstrap remote state (one-time)

Terraform state is never committed. It lives in an S3 bucket + DynamoDB
lock table that aren't part of the `aws/` composition itself (same
chicken-and-egg reasoning as Azure's storage account), so create them once
by hand:

```bash
export TF_STATE_BUCKET=filmpire-tfstate-$RANDOM   # must be globally unique
export TF_STATE_TABLE=filmpire-tfstate-lock
export AWS_REGION=us-east-1                        # see the region note in aws/variables.tf

aws s3api create-bucket \
  --bucket "$TF_STATE_BUCKET" \
  --region "$AWS_REGION" \
  $( [ "$AWS_REGION" != "us-east-1" ] && echo --create-bucket-configuration LocationConstraint="$AWS_REGION" )

aws s3api put-bucket-versioning \
  --bucket "$TF_STATE_BUCKET" \
  --versioning-configuration Status=Enabled

aws s3api put-public-access-block \
  --bucket "$TF_STATE_BUCKET" \
  --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true

aws dynamodb create-table \
  --table-name "$TF_STATE_TABLE" \
  --attribute-definitions AttributeName=LockID,AttributeType=S \
  --key-schema AttributeName=LockID,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST \
  --region "$AWS_REGION"
```

`--create-bucket-configuration` must be **omitted** for `us-east-1`
specifically (S3 rejects an explicit `LocationConstraint` matching that
region) but is required for every other region — the conditional `$(...)`
above handles that. The DynamoDB table stays within the free tier
regardless of demo frequency: `PAY_PER_REQUEST` billing on a table this
small is fractions of a cent per apply/destroy cycle even outside the
25 WCU/RCU always-free allowance.

Save the values (not secret, just account-specific, which is why they're
not hardcoded in `aws/backend.tf`):

```bash
cat > infrastructure/terraform/aws/backend.hcl <<EOF
bucket         = "$TF_STATE_BUCKET"
region         = "$AWS_REGION"
dynamodb_table = "$TF_STATE_TABLE"
EOF
```

`backend.hcl` is gitignored — re-create it locally (or from your password
manager) rather than committing it.

### 2. Credentials

**Local/manual apply:** `aws configure` (stores a long-lived access
key/secret in `~/.aws/credentials`) or `aws configure sso` if your account
uses IAM Identity Center. The aws provider picks either up automatically
via the default credential chain — no `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY`
need to be set in the shell. For a one-person demo workflow a single IAM
user with programmatic access and the permissions below is enough; there's
no equivalent of Azure's separate data-plane role for state (S3 bucket
policies + IAM together cover both management- and data-plane access).

Minimum IAM permissions for `apply`: EC2 (VPC/subnet/security
group/instance/key pair/AMI describe), Budgets (`budgets:*` — the
`aws_budgets_budget` resource), and S3 + DynamoDB scoped to the tfstate
bucket/table from step 1.

**CI — GitHub OIDC, not a stored secret (same pattern as Azure's, adapted
for AWS):** an IAM OIDC identity provider trusts GitHub's token issuer, and
an IAM role trusts that provider for
`repo:pehlivanu/filmpire-microservices:ref:refs/heads/main` specifically.
Set up once:

```bash
aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1

# Trust policy restricts to this repo+branch — see Azure's federated-credential
# subject for the same idea. Attach a least-privilege policy scoped to
# read-only EC2/Budgets describe calls plus S3/DynamoDB read on the tfstate
# bucket/table (enough for `plan`, never `apply` — same split as Azure's CI role).
```

Then set `AWS_ROLE_ARN`, `AWS_REGION`, `TF_STATE_BUCKET`, `TF_STATE_TABLE`,
`ALERT_EMAIL` as **GitHub Actions repo variables** (not Secrets — nothing
here is sensitive without the live OIDC trust). See
`.github/workflows/terraform-plan.yml` for how the Azure side is consumed;
mirror that pattern here if/when this side is wired into the same
workflow — it isn't yet (see "What this doesn't cover yet" below).

### 3. Configure and apply

```bash
cd infrastructure/terraform/aws
cp terraform.tfvars.example terraform.tfvars
```

Edit `terraform.tfvars`: set `alert_emails` and `ssh_public_key` (contents
of e.g. `~/.ssh/id_ed25519.pub`). Both are required with no default on
purpose; see `modules/budget-guard-aws/variables.tf` and
`modules/cluster-k3s/variables.tf`.

```bash
terraform init -backend-config=backend.hcl
terraform plan -out=tfplan
terraform apply tfplan    # a few minutes — EC2 boot + k3s install via user_data
```

### 4. Deploy the app and verify

Unlike AKS, there's no cloud CLI subcommand that writes a kubeconfig for a
self-managed k3s node — fetch it over SSH and point it at the node's public
IP (which, unlike the AKS case, Terraform CAN safely output directly — see
`modules/cluster-k3s/outputs.tf`):

```bash
PUBLIC_IP=$(terraform output -raw public_ip)

# k3s finishes installing shortly after boot; retry if this 404s/connection-refuses.
ssh -o StrictHostKeyChecking=accept-new "$(terraform output -raw ssh_user)@$PUBLIC_IP" \
  sudo cat /etc/rancher/k3s/k3s.yaml > kubeconfig-aws.yaml
export KUBECONFIG=$PWD/kubeconfig-aws.yaml

# --node-external-ip/--tls-san in user_data already made k3s write this
# kubeconfig with the public IP as the server address, so no sed-rewrite
# of `server: https://127.0.0.1:6443` is needed here (contrast the AKS
# node-IP output problem — this is the case where the straightforward
# approach actually works).

kubectl apply -k ../../kubernetes/overlays/aws
kubectl get pods -w   # wait for Running/Ready

curl "http://$PUBLIC_IP:$(terraform output -raw demo_inbound_port)/actuator/health"
```

`overlays/aws` patches the gateway's Service to `NodePort` 30080 (see its
`kustomization.yaml`) so it lands on the same port `modules/network-aws`
opened in the security group. If you change `demo_inbound_port` in
`terraform.tfvars`, update that patch to match.

### 5. Tear down

```bash
terraform destroy
rm -f kubeconfig-aws.yaml
```

This terminates the instance (and its root volume, `delete_on_termination`
defaults to `true` for the root device) plus the VPC/subnet/security
group/budget. Nothing outside this composition needs separate cleanup. Do
this the same session as the demo; nothing here is meant to run
unattended.

### Cost notes (be honest with yourself here)

- The instance: `t3.micro` is free-tier eligible for 750h/month for the
  account's first 12 months — no platform-enforced minimum blocked this
  the way AKS's did (see the Azure section above), since k3s just runs as
  a process on a normal instance rather than needing a managed control
  plane's resource floor.
- The root volume: 20GB `gp3`, within the 30GB/month free EBS allowance.
- No Elastic IP resource: the instance's default public IP (from
  `map_public_ip_on_launch` on the subnet) is used directly instead of
  allocating a separate `aws_eip`. An EIP *not* attached to a running
  instance bills hourly; an instance's default public IP does not, and
  since this is released back on `terraform destroy` anyway, there's no
  reason to add the extra resource.
- The budget-guard module applies a $1 zero-spend tripwire on the account
  **before** anything else, and emails `alert_emails` the same day if
  actual or forecasted spend goes positive — same role as Azure's, not any
  specific instance type's "free-tier" label.
- No ELB/NAT gateway: same reasoning as Azure's Standard LB avoidance — both
  bill hourly regardless of traffic, disproportionate to a demo.

### What this doesn't cover yet

- Not live-tested against a real AWS account yet — this section, unlike
  the Azure one, is written from the AWS provider/API docs and k3s's own
  install docs, not from an actual `apply` → `kubectl apply -k` →
  `destroy` round-trip. Static `validate` passed, but the Azure section
  above is a reminder that live API/quota/region behavior routinely
  surfaces things `validate`/`plan` can't catch (AKS's SKU floor, blocked
  VM families, the wrong-IP data source). Re-verify every assumption here
  (instance type availability in your account/region, IMDSv2 behavior,
  whether the free-tier credits model has changed again) against a real
  apply before trusting it fully.
- Not wired into `.github/workflows/terraform-plan.yml` — that workflow
  currently only plans the Azure side.
- The app itself would hit the same `#28` (CI/CD image publish) blocker
  the Azure side did — `ghcr.io/pehlivanu/filmpire-*:latest` isn't
  published yet.
