#!/usr/bin/env bash
set -euo pipefail

: "${KUBE_NAMESPACE:=hangyin}"
: "${FAULT_SERVICE_URL:=http://127.0.0.1:18082/api/live/srs/health}"
: "${FAULT_OUTPUT_DIR:=${RUNNER_TEMP:-/tmp}/hangyin-fault/live-service}"
: "${FAULT_REQUEST_TIMEOUT_SECONDS:=8}"
: "${FAULT_POLL_SECONDS:=45}"

mkdir -p "${FAULT_OUTPUT_DIR}"
RESULTS="${FAULT_OUTPUT_DIR}/fault-results.csv"
echo 'scenario,phase,expected_reachable,observed_reachable,http_ok,duration_ms,passed,timestamp' > "${RESULTS}"

record_health() {
  local scenario="$1" phase="$2" expected="$3" attempt="$4"
  local body_file="${FAULT_OUTPUT_DIR}/${scenario}-${phase}-${attempt}.json"
  local start end duration curl_ok observed passed timestamp
  start="$(date +%s%3N)"
  curl_ok=false
  if curl -fsS --connect-timeout 2 --max-time "${FAULT_REQUEST_TIMEOUT_SECONDS}" \
      "${FAULT_SERVICE_URL}" -o "${body_file}"; then
    curl_ok=true
  fi
  end="$(date +%s%3N)"
  duration=$((end - start))
  observed="unknown"
  if [[ -s "${body_file}" ]]; then
    observed="$(jq -r '.data.reachable | tostring' "${body_file}" 2>/dev/null || echo unknown)"
  fi
  passed=false
  if [[ "${curl_ok}" == true ]] && [[ "${observed}" == "${expected}" ]]; then passed=true; fi
  timestamp="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf '%s,%s,%s,%s,%s,%s,%s,%s\n' \
    "${scenario}" "${phase}" "${expected}" "${observed}" "${curl_ok}" \
    "${duration}" "${passed}" "${timestamp}" >> "${RESULTS}"
  [[ "${passed}" == true ]]
}

poll_health() {
  local scenario="$1" phase="$2" expected="$3" deadline=$((SECONDS + FAULT_POLL_SECONDS)) attempt=1
  while (( SECONDS < deadline )); do
    if record_health "${scenario}" "${phase}" "${expected}" "${attempt}"; then return 0; fi
    attempt=$((attempt + 1))
    sleep 2
  done
  tail -n 20 "${RESULTS}" >&2
  return 1
}

rollout() { kubectl -n "${KUBE_NAMESPACE}" rollout status deployment/"$1" --timeout=180s; }
restore_dependencies() {
  kubectl -n "${KUBE_NAMESPACE}" scale deployment/srs --replicas=1 >/dev/null 2>&1 || true
  rollout srs >/dev/null 2>&1 || true
  kubectl -n "${KUBE_NAMESPACE}" set env deployment/live-service LIVE_SRS_API_BASE_URL=http://srs:1985 >/dev/null 2>&1 || true
}
trap restore_dependencies EXIT

poll_health srs-baseline baseline true

kubectl -n "${KUBE_NAMESPACE}" scale deployment/srs --replicas=0
kubectl -n "${KUBE_NAMESPACE}" get deployment/srs -o wide || true
kubectl -n "${KUBE_NAMESPACE}" get pods -o wide || true
poll_health srs-outage degraded false

kubectl -n "${KUBE_NAMESPACE}" scale deployment/srs --replicas=1
rollout srs
poll_health srs-outage recovered true

kubectl -n "${KUBE_NAMESPACE}" set env deployment/live-service \
  LIVE_SRS_API_BASE_URL=http://192.0.2.1:1985
rollout live-service
poll_health srs-timeout timeout false

kubectl -n "${KUBE_NAMESPACE}" set env deployment/live-service \
  LIVE_SRS_API_BASE_URL=http://srs:1985
rollout live-service
poll_health srs-timeout recovered true

kubectl -n "${KUBE_NAMESPACE}" delete pod \
  -l app.kubernetes.io/name=live-service --wait=false
rollout live-service
poll_health live-service-pod-restart recovered true

cat "${RESULTS}"
