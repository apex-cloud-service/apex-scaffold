package com.apex.scaffold.tools.build;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class KubernetesReleaseContractTest {

    private final Path root = BuildManifestSyncTest.projectRoot();

    @Test
    void stableAndCanaryResourcesUseDisjointTrackSelectors() throws Exception {
        String stable = Files.readString(root.resolve("k8s/deployment.yaml"));
        String canary = Files.readString(root.resolve("k8s/canary-deployment.yaml"));
        String pdb = Files.readString(root.resolve("k8s/pod-disruption-budget.yaml"));
        String ingress = Files.readString(root.resolve("k8s/canary-ingress.yaml"));

        assertTrue(stable.contains("selector:\n    matchLabels:\n      app: apex-gateway\n  strategy:"));
        assertTrue(stable.contains("labels:\n        app: apex-gateway\n        track: stable"));
        assertTrue(stable.contains("selector:\n    app: apex-gateway\n    track: stable"));
        assertTrue(canary.contains("track: canary"));
        assertTrue(canary.contains("name: apex-gateway-canary-service"));
        assertTrue(!canary.contains("kind: Ingress"));
        assertTrue(pdb.contains("track: stable"));
        assertTrue(ingress.contains("nginx.ingress.kubernetes.io/canary-by-weight: \"0\""));
    }

    @Test
    void deploymentOrchestrationFailsClosedAndChecksActualGatewayEndpoints() throws Exception {
        String script = Files.readString(root.resolve("apex-tools-parent/apex-tools-build/bin/apex.sh"));
        String jq = Files.readString(root.resolve("apex-tools-parent/apex-tools-build/bin/gateway-endpoints.jq"));
        String ingressPolicy =
                Files.readString(root.resolve("apex-tools-parent/apex-tools-build/bin/canary-ingress.jq"));

        assertTrue(script.contains("k8s_require_stable_layout 0"));
        assertTrue(script.contains("k8s_rollout apex-gateway 300s"));
        assertTrue(script.contains("k8s_rollout apex-gateway-canary 300s"));
        assertTrue(script.contains("k8s_wait_gateway_endpoints 1"));
        assertTrue(script.contains("canary-ingress.yaml"));
        assertTrue(!script.contains("deployment/apex-scaffold"));
        assertTrue(jq.contains("conditions.ready == true"));
        assertTrue(jq.contains("targetRef.uid"));
        assertTrue(jq.contains("track == $track"));
        assertTrue(script.contains("spec.controller == $controller"));
        assertTrue(ingressPolicy.contains(".spec.ingressClassName == $class"));
    }
}
