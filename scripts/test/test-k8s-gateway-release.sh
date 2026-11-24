#!/usr/bin/env bash

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CLI="${ROOT}/apex-tools-parent/apex-tools-build/bin/apex.sh"
ENDPOINT_POLICY="${ROOT}/apex-tools-parent/apex-tools-build/bin/gateway-endpoints.jq"
INGRESS_POLICY="${ROOT}/apex-tools-parent/apex-tools-build/bin/canary-ingress.jq"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TEMP_DIR}"' EXIT

cat > "${TEMP_DIR}/kubectl" <<'MOCK'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "${K8S_KUBECTL_LOG}"
case "$*" in
  'get ns --request-timeout=10s') printf '{"items":[]}' ;;
  *'get endpointslices,pods,replicasets,deployments'*) printf '%s' "${K8S_ENDPOINT_FIXTURE}" ;;
  *'get ingress/apex-gateway-canary-ingress'*) printf '%s' "${K8S_CANARY_INGRESS}" ;;
  *'get ingress/apex-scaffold-ingress'*) printf '%s' "${K8S_STABLE_INGRESS}" ;;
  *'get ingressclass/nginx'*)
    [[ "${K8S_INGRESS_MODE}" != missing ]] || exit 1
    printf '{"metadata":{"name":"nginx"},"spec":{"controller":"%s"}}' \
      "$([[ "${K8S_INGRESS_MODE}" == wrong ]] && printf wrong || printf k8s.io/ingress-nginx)"
    ;;
  *'get service/apex-gateway'*)
    if [[ "${K8S_SELECTOR_STATE}" == stable ]]; then
      printf '{"spec":{"selector":{"app":"apex-gateway","track":"stable"}}}'
    else
      printf '{"spec":{"selector":{"app":"apex-gateway"}}}'
    fi
    ;;
  *'get deployment/apex-gateway'*)
    if [[ "${K8S_SELECTOR_STATE}" == stable || "${K8S_SELECTOR_STATE}" == partial ]]; then
      printf '{"spec":{"template":{"metadata":{"labels":{"app":"apex-gateway","track":"stable"}}}}}'
    else
      printf '{"spec":{"template":{"metadata":{"labels":{"app":"apex-gateway"}}}}}'
    fi
    ;;
  *) exit 0 ;;
esac
MOCK
chmod +x "${TEMP_DIR}/kubectl"

for selector_state in old partial; do
  export K8S_KUBECTL_LOG="${TEMP_DIR}/${selector_state}.log"
  export K8S_SELECTOR_STATE="${selector_state}"
  : > "${K8S_KUBECTL_LOG}"
  if PATH="${TEMP_DIR}:${PATH}" zsh "${CLI}" deploy k8s apply --allow-placeholder >/dev/null 2>&1; then
    printf 'unexpectedly accepted %s stable selector topology\n' "${selector_state}" >&2
    exit 1
  fi
  if grep -q '^apply ' "${K8S_KUBECTL_LOG}"; then
    printf 'applied resources before rejecting %s selector topology\n' "${selector_state}" >&2
    exit 1
  fi
done

endpoint_fixture() {
  local canary_uid="$1"
  jq -n --arg canary_uid "${canary_uid}" '{items:[
    {kind:"Deployment",metadata:{name:"apex-gateway",uid:"stable-deploy"},spec:{template:{metadata:{labels:{track:"stable"}}}}},
    {kind:"Deployment",metadata:{name:"apex-gateway-canary",uid:"canary-deploy"},spec:{template:{metadata:{labels:{track:"canary"}}}}},
    {kind:"ReplicaSet",metadata:{uid:"stable-rs",ownerReferences:[{controller:true,kind:"Deployment",uid:"stable-deploy"}]}},
    {kind:"ReplicaSet",metadata:{uid:"canary-rs",ownerReferences:[{controller:true,kind:"Deployment",uid:"canary-deploy"}]}},
    {kind:"Pod",metadata:{uid:"stable-pod",labels:{app:"apex-gateway",track:"stable"},ownerReferences:[{controller:true,kind:"ReplicaSet",uid:"stable-rs"}]}},
    {kind:"Pod",metadata:{uid:"canary-pod",labels:{app:"apex-gateway",track:"canary"},ownerReferences:[{controller:true,kind:"ReplicaSet",uid:"canary-rs"}]}},
    {kind:"EndpointSlice",metadata:{labels:{"kubernetes.io/service-name":"apex-gateway"}},endpoints:[{conditions:{ready:true,terminating:false},targetRef:{kind:"Pod",uid:"stable-pod"}}]},
    {kind:"EndpointSlice",metadata:{labels:{"kubernetes.io/service-name":"apex-gateway-canary-service"}},endpoints:[{conditions:{ready:true,terminating:false},targetRef:{kind:"Pod",uid:$canary_uid}}]}
  ]}'
}

export K8S_SELECTOR_STATE=stable
export K8S_ENDPOINT_FIXTURE="$(endpoint_fixture canary-pod)"
export K8S_STABLE_INGRESS='{"spec":{"ingressClassName":"nginx","rules":[{"host":"apex.example.test","http":{"paths":[{"path":"/","pathType":"Prefix","backend":{"service":{"name":"apex-gateway","port":{"number":80}}}}]}}]}}'
export K8S_CANARY_INGRESS='{"metadata":{"annotations":{"nginx.ingress.kubernetes.io/canary":"true","nginx.ingress.kubernetes.io/canary-by-weight":"0"}},"spec":{"ingressClassName":"nginx","rules":[{"host":"apex.example.test","http":{"paths":[{"path":"/","pathType":"Prefix","backend":{"service":{"name":"apex-gateway-canary-service","port":{"number":80}}}}]}}]}}'
for ingress_mode in missing wrong; do
  export K8S_INGRESS_MODE="${ingress_mode}"
  export K8S_KUBECTL_LOG="${TEMP_DIR}/ingress-${ingress_mode}.log"
  : > "${K8S_KUBECTL_LOG}"
  if PATH="${TEMP_DIR}:${PATH}" zsh "${CLI}" deploy k8s check-canary >/dev/null 2>&1; then
    printf 'unexpectedly accepted %s Nginx IngressClass controller\n' "${ingress_mode}" >&2
    exit 1
  fi
  if grep -q '^apply ' "${K8S_KUBECTL_LOG}"; then
    printf 'created a canary Ingress with %s IngressClass controller\n' "${ingress_mode}" >&2
    exit 1
  fi
done

endpoint_fixture canary-pod | jq -e --argjson canary 1 -f "${ENDPOINT_POLICY}" >/dev/null
for invalid_uid in unknown-pod stable-pod; do
  if endpoint_fixture "${invalid_uid}" | jq -e --argjson canary 1 -f "${ENDPOINT_POLICY}" >/dev/null 2>&1; then
    printf 'unexpectedly accepted invalid canary endpoint UID %s\n' "${invalid_uid}" >&2
    exit 1
  fi
done

stable_ingress='{"spec":{"ingressClassName":"nginx","rules":[{"host":"apex.example.test","http":{"paths":[{"path":"/","pathType":"Prefix","backend":{"service":{"name":"apex-gateway","port":{"number":80}}}}]}}]}}'
candidate_ingress='{"metadata":{"annotations":{"nginx.ingress.kubernetes.io/canary":"true","nginx.ingress.kubernetes.io/canary-by-weight":"0"}},"spec":{"ingressClassName":"nginx","rules":[{"host":"apex.example.test","http":{"paths":[{"path":"/","pathType":"Prefix","backend":{"service":{"name":"apex-gateway-canary-service","port":{"number":80}}}}]}}]}}'
echo "${candidate_ingress}" | jq -e --argjson stable "${stable_ingress}" --argjson zero true \
  --arg class nginx -f "${INGRESS_POLICY}" >/dev/null
for invalid_ingress in \
  "$(jq '.spec.ingressClassName = null' <<< "${candidate_ingress}")" \
  "$(jq '.spec.ingressClassName = "other"' <<< "${candidate_ingress}")" \
  "$(jq '.spec.rules[0].host = "wrong.example.test"' <<< "${candidate_ingress}")" \
  "$(jq '.metadata.annotations["nginx.ingress.kubernetes.io/canary-by-header"] = "X-Canary"' <<< "${candidate_ingress}")" \
  "$(jq '.metadata.annotations["nginx.ingress.kubernetes.io/canary-by-weight"] = "10"' <<< "${candidate_ingress}")"; do
  if jq -e --argjson stable "${stable_ingress}" --argjson zero true --arg class nginx \
    -f "${INGRESS_POLICY}" <<< "${invalid_ingress}" >/dev/null 2>&1; then
    printf 'unexpectedly accepted an unsafe canary Ingress policy\n' >&2
    exit 1
  fi
done

printf 'Kubernetes gateway release behavior checks passed\n'
