# ServiceMonitor contract

Each file here targets one service by its pod's container port (`targetPort`,
not a named Service port — so these don't depend on what #25's Kubernetes
Service manifests decide to *name* their ports). What they DO depend on:

- The Service must carry the label `monitoring: enabled` (ARCHITECTURE.md
  section 12.1) — that's what `spec.selector.matchLabels` matches on here.
- The Service must live in the `default` namespace (`namespaceSelector`
  below). Adjust both if issue #25 puts services in a dedicated namespace.

| Service            | targetPort |
|---------------------|-----------|
| api-gateway            | 8080 |
| movie-service          | 8081 |
| user-service            | 8082 |
| actor-service            | 8083 |
| ai-service                | 8084 |
| media-service              | 8085 |

discovery-service and config-service have no monitor here: they are not
deployed to Kubernetes at all (ADR-005). media-service has one, but it is not
in the Kubernetes base yet, so it currently matches nothing.

Apply after the kube-prometheus-stack release and the target Services both
exist:

```
kubectl apply -f infrastructure/kubernetes/monitoring/service-monitors/
```
