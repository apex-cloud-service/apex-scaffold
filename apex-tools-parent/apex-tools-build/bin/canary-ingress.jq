"nginx.ingress.kubernetes.io/" as $prefix
| (.metadata.annotations // {}) as $annotations
| $annotations[$prefix + "canary-by-weight"] as $weight
| ($annotations[$prefix + "canary"] == "true")
  and ($weight | type == "string" and test("^(0|[1-9][0-9]?|100)$"))
  and (if $zero then $weight == "0" else true end)
  and all($annotations | keys[] | select(startswith($prefix + "canary"));
    . == ($prefix + "canary") or . == ($prefix + "canary-by-weight"))
  and ($stable.metadata.annotations[$prefix + "affinity"] == null)
  and (.spec.ingressClassName == $class)
  and ($stable.spec.ingressClassName == $class)
  and (.metadata.annotations["kubernetes.io/ingress.class"] == null or
    .metadata.annotations["kubernetes.io/ingress.class"] == $class)
  and ($stable.metadata.annotations["kubernetes.io/ingress.class"] == null or
    $stable.metadata.annotations["kubernetes.io/ingress.class"] == $class)
  and (.spec.defaultBackend == null and (.spec.rules | length) == 1)
  and (.spec.rules[0] as $candidate_rule
    | ($candidate_rule.host | type == "string" and length > 0)
      and ($candidate_rule.http.paths | length) == 1
      and ($candidate_rule.http.paths[0] as $candidate_path
        | $candidate_path.backend.service.name == "apex-gateway-canary-service"
          and $candidate_path.backend.service.port.number == 80
          and any($stable.spec.rules[] | select(.host == $candidate_rule.host) | .http.paths[];
            .path == $candidate_path.path and .pathType == $candidate_path.pathType
            and .backend.service.name == "apex-gateway" and .backend.service.port.number == 80)))
