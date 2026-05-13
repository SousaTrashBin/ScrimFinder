"""
infrastructure/pulumi/__main__.py

Provisions the ScrimFinder GKE cluster and surrounding GCP infrastructure.
Replaces the ad-hoc `gcloud container clusters create` block in deploy.sh.

Usage
-----
  cd infrastructure/pulumi
  pulumi stack select dev        # or prod
  pulumi up
  pulumi stack output kubeconfig --show-secrets

Config keys (set per stack)
---------------------------
  gcp:project / gcp:region
  scrimfinder:cluster_name, zone_suffix, node_machine_type, node_count,
  min_nodes, max_nodes, disk_size_gb, repo_name, enable_spot,
  gke_channel, master_cidr
"""

import pulumi
import pulumi_gcp as gcp
from pulumi import Output

cfg = pulumi.Config("scrimfinder")
gcp_cfg = pulumi.Config("gcp")

PROJECT = gcp_cfg.require("project")
REGION = gcp_cfg.require("region")
ZONE = f"{REGION}-{cfg.get('zone_suffix') or 'a'}"
CLUSTER_NAME = cfg.get("cluster_name") or "scrimfinder"
MACHINE_TYPE = cfg.get("node_machine_type") or "e2-standard-4"
NODE_COUNT = cfg.get_int("node_count") or 1
MIN_NODES = cfg.get_int("min_nodes") or 1
MAX_NODES = cfg.get_int("max_nodes") or 3
DISK_SIZE_GB = cfg.get_int("disk_size_gb") or 40
REPO_NAME = cfg.get("repo_name") or "scrimfinder"
ENABLE_SPOT = cfg.get_bool("enable_spot") if cfg.get("enable_spot") else True
GKE_CHANNEL = cfg.get("gke_channel") or "REGULAR"
MASTER_CIDR = cfg.get("master_cidr") or "0.0.0.0/0"
stack = pulumi.get_stack()

# ── Enable GCP APIs ───────────────────────────────────────────────────────────

_apis = [
    "container.googleapis.com",
    "artifactregistry.googleapis.com",
    "iam.googleapis.com",
    "compute.googleapis.com",
]
api_enablements = {
    svc: gcp.projects.Service(
        f"api-{svc.replace('.', '-')}",
        project=PROJECT,
        service=svc,
        disable_on_destroy=False,
    )
    for svc in _apis
}

# ── VPC & Subnet ──────────────────────────────────────────────────────────────

network = gcp.compute.Network(
    f"scrimfinder-vpc-{stack}",
    project=PROJECT,
    auto_create_subnetworks=False,
    description=f"ScrimFinder VPC ({stack})",
    opts=pulumi.ResourceOptions(depends_on=list(api_enablements.values())),
)

subnet = gcp.compute.Subnetwork(
    f"scrimfinder-subnet-{stack}",
    project=PROJECT,
    region=REGION,
    network=network.id,
    ip_cidr_range="10.0.0.0/20",
    secondary_ip_ranges=[
        gcp.compute.SubnetworkSecondaryIpRangeArgs(
            range_name="pods", ip_cidr_range="10.4.0.0/14"
        ),
        gcp.compute.SubnetworkSecondaryIpRangeArgs(
            range_name="services", ip_cidr_range="10.8.0.0/20"
        ),
    ],
    private_ip_google_access=True,
)

# ── GKE Cluster (control plane only) ─────────────────────────────────────────
# remove_default_node_pool=True is the GCP-recommended pattern: lets us update
# the NodePool (machine type, disk, spot) without destroying the control plane.

cluster = gcp.container.Cluster(
    f"scrimfinder-cluster-{stack}",
    project=PROJECT,
    location=ZONE,
    name=CLUSTER_NAME,
    remove_default_node_pool=True,
    initial_node_count=1,
    network=network.self_link,
    subnetwork=subnet.self_link,
    ip_allocation_policy=gcp.container.ClusterIpAllocationPolicyArgs(
        cluster_secondary_range_name="pods",
        services_secondary_range_name="services",
    ),
    workload_identity_config=gcp.container.ClusterWorkloadIdentityConfigArgs(
        workload_pool=f"{PROJECT}.svc.id.goog",
    ),
    release_channel=gcp.container.ClusterReleaseChannelArgs(channel=GKE_CHANNEL),
    private_cluster_config=gcp.container.ClusterPrivateClusterConfigArgs(
        enable_private_nodes=True,
        enable_private_endpoint=False,
        master_ipv4_cidr_block="172.16.0.0/28",
    ),
    master_authorized_networks_config=gcp.container.ClusterMasterAuthorizedNetworksConfigArgs(
        cidr_blocks=[
            gcp.container.ClusterMasterAuthorizedNetworksConfigCidrBlockArgs(
                cidr_block=MASTER_CIDR,
                display_name="allowed",
            )
        ]
    ),
    addons_config=gcp.container.ClusterAddonsConfigArgs(
        http_load_balancing=gcp.container.ClusterAddonsConfigHttpLoadBalancingArgs(
            disabled=False
        ),
        horizontal_pod_autoscaling=gcp.container.ClusterAddonsConfigHorizontalPodAutoscalingArgs(
            disabled=False
        ),
    ),
    opts=pulumi.ResourceOptions(depends_on=list(api_enablements.values())),
)

# ── Node SA (least-privilege) ─────────────────────────────────────────────────

node_sa = gcp.serviceaccount.Account(
    f"scrimfinder-node-sa-{stack}",
    project=PROJECT,
    account_id=f"sf-node-{stack}",
    display_name=f"ScrimFinder GKE Node SA ({stack})",
)

node_sa_bindings = [
    gcp.projects.IAMMember(
        f"node-sa-{role.replace('/', '-')}-{stack}",
        project=PROJECT,
        role=role,
        member=node_sa.email.apply(lambda e: f"serviceAccount:{e}"),
    )
    for role in [
        "roles/logging.logWriter",
        "roles/monitoring.metricWriter",
        "roles/monitoring.viewer",
        "roles/artifactregistry.reader",
    ]
]

# ── Node Pool ─────────────────────────────────────────────────────────────────

node_pool = gcp.container.NodePool(
    f"scrimfinder-nodes-{stack}",
    project=PROJECT,
    location=ZONE,
    cluster=cluster.name,
    name=f"default-pool-{stack}",
    initial_node_count=NODE_COUNT,
    autoscaling=gcp.container.NodePoolAutoscalingArgs(
        min_node_count=MIN_NODES,
        max_node_count=MAX_NODES,
    ),
    management=gcp.container.NodePoolManagementArgs(
        auto_repair=True, auto_upgrade=True
    ),
    node_config=gcp.container.NodePoolNodeConfigArgs(
        machine_type=MACHINE_TYPE,
        disk_size_gb=DISK_SIZE_GB,
        disk_type="pd-standard",
        spot=ENABLE_SPOT,
        service_account=node_sa.email,
        oauth_scopes=["https://www.googleapis.com/auth/cloud-platform"],
        workload_metadata_config=gcp.container.NodePoolNodeConfigWorkloadMetadataConfigArgs(
            mode="GKE_METADATA",
        ),
        shielded_instance_config=gcp.container.NodePoolNodeConfigShieldedInstanceConfigArgs(
            enable_secure_boot=True,
            enable_integrity_monitoring=True,
        ),
    ),
    opts=pulumi.ResourceOptions(depends_on=node_sa_bindings),
)

# ── Artifact Registry ─────────────────────────────────────────────────────────

artifact_repo = gcp.artifactregistry.Repository(
    f"scrimfinder-repo-{stack}",
    project=PROJECT,
    location=REGION,
    repository_id=REPO_NAME,
    format="DOCKER",
    description=f"ScrimFinder Docker images ({stack})",
    opts=pulumi.ResourceOptions(
        depends_on=[api_enablements["artifactregistry.googleapis.com"]]
    ),
)

# ── Workload Identity bindings ────────────────────────────────────────────────


def _workload_identity(svc_name: str, ns: str = "scrimfinder"):
    gsa = gcp.serviceaccount.Account(
        f"sf-{svc_name}-gsa-{stack}",
        project=PROJECT,
        account_id=f"sf-{svc_name}-{stack}"[:30],
        display_name=f"ScrimFinder {svc_name} GSA ({stack})",
    )
    gcp.serviceaccount.IAMMember(
        f"sf-{svc_name}-wi-{stack}",
        service_account_id=gsa.name,
        role="roles/iam.workloadIdentityUser",
        member=Output.concat(
            "serviceAccount:",
            PROJECT,
            ".svc.id.goog[",
            ns,
            "/",
            svc_name,
            "]",
        ),
    )
    return gsa


_workload_identity("training-service")
_workload_identity("analysis-service")
_workload_identity("jwt-manager")

# ── kubeconfig export (used by deploy.sh) ─────────────────────────────────────

kubeconfig = Output.all(cluster.name, cluster.endpoint, cluster.master_auth).apply(
    lambda a: (
        f"""apiVersion: v1
clusters:
- cluster:
    certificate-authority-data: {a[2]["cluster_ca_certificate"]}
    server: https://{a[1]}
  name: {a[0]}
contexts:
- context:
    cluster: {a[0]}
    user: {a[0]}
  name: {a[0]}
current-context: {a[0]}
kind: Config
preferences: {{}}
users:
- name: {a[0]}
  user:
    exec:
      apiVersion: client.authentication.k8s.io/v1beta1
      command: gke-gcloud-auth-plugin
      installHint: Install via gcloud components install gke-gcloud-auth-plugin
      provideClusterInfo: true
"""
    )
)

pulumi.export("cluster_name", cluster.name)
pulumi.export("cluster_endpoint", cluster.endpoint)
pulumi.export("zone", ZONE)
pulumi.export("region", REGION)
pulumi.export("project", PROJECT)
pulumi.export(
    "artifact_registry",
    Output.concat(
        REGION, "-docker.pkg.dev/", PROJECT, "/", artifact_repo.repository_id
    ),
)
pulumi.export("node_sa_email", node_sa.email)
pulumi.export("kubeconfig", kubeconfig)
