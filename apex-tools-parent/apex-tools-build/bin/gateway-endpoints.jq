def controlled_by($resource; $kind):
  any(.metadata.ownerReferences[]?;
      .controller == true and .kind == $kind and .uid == $resource.metadata.uid);

def endpoints($service):
  [.items[] | select(.kind == "EndpointSlice"
    and .metadata.labels["kubernetes.io/service-name"] == $service) | .endpoints[]?];

def healthy_pool($service; $workload; $track):
  [.items[] | select(.kind == "Deployment" and .metadata.name == $workload
    and .spec.template.metadata.labels.track == $track)] as $deployments
  | [$deployments[] as $deployment | .items[] | select(.kind == "ReplicaSet"
      and controlled_by($deployment; "Deployment"))] as $replica_sets
  | [$replica_sets[] as $replica_set | .items[] | select(.kind == "Pod"
      and .metadata.labels.app == "apex-gateway" and .metadata.labels.track == $track
      and controlled_by($replica_set; "ReplicaSet")) | .metadata.uid] as $pod_uids
  | endpoints($service) as $endpoints
  | ($deployments | length) == 1
    and any($endpoints[]; .conditions.ready == true and .conditions.terminating != true)
    and all($endpoints[]; .targetRef as $target
      | $target.kind == "Pod" and ($pod_uids | index($target.uid)) != null);

healthy_pool("apex-gateway"; "apex-gateway"; "stable")
and (if $canary == 1 then
  healthy_pool("apex-gateway-canary-service"; "apex-gateway-canary"; "canary")
  and ([endpoints("apex-gateway")[].targetRef.uid] as $stable
    | all(endpoints("apex-gateway-canary-service")[].targetRef.uid;
        . as $uid | ($stable | index($uid)) == null))
else true end)
