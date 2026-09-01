#!/usr/bin/env bash
set -euo pipefail

: "${KUBE_NAMESPACE:=hangyin}"
: "${HPA_DEPLOYMENT:=live-service}"
: "${HPA_SERVICE_URL:=http://127.0.0.1:18080/api/live/rooms?page=1&pageSize=10}"
: "${HPA_OUTPUT_DIR:=${RUNNER_TEMP:-/tmp}/hangyin-hpa}"
: "${HPA_WORKERS:=32}"
: "${HPA_LOAD_SECONDS:=75}"

mkdir -p "${HPA_OUTPUT_DIR}"
rm -f "${HPA_OUTPUT_DIR}"/*
SAMPLES="${HPA_OUTPUT_DIR}/hpa-samples.csv"
SUMMARY="${HPA_OUTPUT_DIR}/summary.csv"
REQUESTS="${HPA_OUTPUT_DIR}/requests.tsv"
PIDS=()
echo 'timestamp,desired,current,ready,cpu,target,conditions' > "${SAMPLES}"
echo 'phase,requests,successes,errors,throughput_rps,avg_ms,p95_ms,error_rate' > "${SUMMARY}"

cleanup() {
  for pid in "${PIDS[@]:-}"; do kill "${pid}" 2>/dev/null || true; done
  wait 2>/dev/null || true
}
trap cleanup EXIT

sample() {
  local now desired current ready cpu target conditions
  now="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  desired="$(kubectl -n "${KUBE_NAMESPACE}" get hpa "${HPA_DEPLOYMENT}" -o jsonpath='{.status.desiredReplicas}' 2>/dev/null || echo 0)"
  current="$(kubectl -n "${KUBE_NAMESPACE}" get hpa "${HPA_DEPLOYMENT}" -o jsonpath='{.status.currentReplicas}' 2>/dev/null || echo 0)"
  ready="$(kubectl -n "${KUBE_NAMESPACE}" get deployment "${HPA_DEPLOYMENT}" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)"
  cpu="$(kubectl -n "${KUBE_NAMESPACE}" get hpa "${HPA_DEPLOYMENT}" -o jsonpath='{.status.currentMetrics[0].resource.current.averageUtilization}' 2>/dev/null || echo 0)"
  target="$(kubectl -n "${KUBE_NAMESPACE}" get hpa "${HPA_DEPLOYMENT}" -o jsonpath='{.spec.metrics[0].resource.target.averageUtilization}' 2>/dev/null || echo 50)"
  conditions="$(kubectl -n "${KUBE_NAMESPACE}" get hpa "${HPA_DEPLOYMENT}" -o jsonpath='{range .status.conditions[*]}{.type}={.status};{end}' 2>/dev/null || echo Unknown)"
  echo "${now},${desired:-0},${current:-0},${ready:-0},${cpu:-0},${target:-50},${conditions:-Unknown}" >> "${SAMPLES}"
  echo "${now} desired=${desired:-0} ready=${ready:-0} cpu=${cpu:-0}%"
}

wait_metrics() {
  local deadline=$((SECONDS + 180))
  while (( SECONDS < deadline )); do
    if kubectl top pods -n "${KUBE_NAMESPACE}" -l app.kubernetes.io/name="${HPA_DEPLOYMENT}" >/dev/null 2>&1; then return 0; fi
    sleep 5
  done
  return 1
}

worker() {
  local end=$((SECONDS + HPA_LOAD_SECONDS)) response
  while (( SECONDS < end )); do
    response="$(curl -sS --max-time 10 -o /dev/null -w '%{http_code} %{time_total}' "${HPA_SERVICE_URL}" 2>/dev/null || echo '000 10')"
    printf '%s\t%s\n' "${response%% *}" "${response##* }" >> "${REQUESTS}"
  done
}

summarize() {
  local phase="$1" total successes errors avg p95
  total="$(awk -F '\t' 'NF>=2{n++} END{print n+0}' "${REQUESTS}")"
  successes="$(awk -F '\t' '$1 ~ /^2/{n++} END{print n+0}' "${REQUESTS}")"
  errors=$((total - successes))
  avg="$(awk -F '\t' '{sum+=$2} END{printf "%.2f",NR?sum/NR*1000:0}' "${REQUESTS}")"
  p95="$(awk -F '\t' '{print $2*1000}' "${REQUESTS}" | sort -n | awk '{a[NR]=$1} END{if(NR){i=int((NR*95+99)/100);printf "%.2f",a[i]}else print "0.00"}')"
  echo "${phase},${total},${successes},${errors},$(awk -v n="$total" -v s="$HPA_LOAD_SECONDS" 'BEGIN{printf "%.2f",s>0?n/s:0}'),${avg},${p95},$(awk -v e="$errors" -v n="$total" 'BEGIN{printf "%.2f",n>0?e*100/n:0}')" >> "${SUMMARY}"
  : > "${REQUESTS}"
}

wait_metrics
sample
for _ in $(seq 1 "${HPA_WORKERS}"); do worker & PIDS+=("$!"); done
up_seen=0
deadline=$((SECONDS + 180))
while (( SECONDS < deadline )); do
  sample
  ready="$(kubectl -n "${KUBE_NAMESPACE}" get deployment "${HPA_DEPLOYMENT}" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)"
  if [[ "${ready:-0}" =~ ^[0-9]+$ ]] && (( ready > 1 )); then up_seen=1; break; fi
  sleep 5
done
for pid in "${PIDS[@]}"; do wait "${pid}" || true; done
summarize load
if (( up_seen == 0 )); then kubectl -n "${KUBE_NAMESPACE}" describe hpa "${HPA_DEPLOYMENT}" || true; exit 1; fi

down_seen=0
deadline=$((SECONDS + 180))
while (( SECONDS < deadline )); do
  sample
  ready="$(kubectl -n "${KUBE_NAMESPACE}" get deployment "${HPA_DEPLOYMENT}" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo 0)"
  if [[ "${ready:-0}" == '1' ]]; then down_seen=1; break; fi
  sleep 5
done
if (( down_seen == 0 )); then kubectl -n "${KUBE_NAMESPACE}" describe hpa "${HPA_DEPLOYMENT}" || true; exit 1; fi
cat "${SUMMARY}"
