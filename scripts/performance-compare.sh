#!/usr/bin/env bash
set -euo pipefail

: "${PERF_OUTPUT_DIR:=${RUNNER_TEMP:-/tmp}/hangyin-performance}"
: "${PERF_CONCURRENCY:=16}"
: "${PERF_DURATION_SECONDS:=30}"
: "${PERF_REPETITIONS:=3}"
: "${PERF_MONOLITH_URL:=http://127.0.0.1:18081}"
: "${PERF_MICROSERVICE_URL:=http://127.0.0.1:18082}"
: "${PERF_MONOLITH_PID:=}"
: "${PERF_MICROSERVICE_PID:=}"
: "${PERF_MONOLITH_LOG:=}"
: "${PERF_MICROSERVICE_LOG:=}"

mkdir -p "${PERF_OUTPUT_DIR}"
rm -f "${PERF_OUTPUT_DIR}"/*
RESULTS="${PERF_OUTPUT_DIR}/performance-results.csv"
echo 'mode,endpoint,repetition,concurrency,duration_seconds,requests,successes,errors,throughput_rps,avg_ms,p95_ms,error_rate,cpu_avg_pct,rss_peak_mb' > "${RESULTS}"

wait_ready() {
  local name="$1" base="$2" pid="$3" log="$4"
  local deadline=$((SECONDS + 180)) body="${PERF_OUTPUT_DIR}/${name}-ready.body"
  while (( SECONDS < deadline )); do
    if [[ -n "${pid}" ]] && ! kill -0 "${pid}" 2>/dev/null; then
      echo "${name} process ${pid} exited before readiness" >&2
      [[ -z "${log}" ]] || tail -n 120 "${log}" >&2 || true
      return 1
    fi
    if curl -fsS --connect-timeout 2 --max-time 5 \
      "${base}/api/live/rooms?page=1&pageSize=10" -o "${body}"; then
      return 0
    fi
    sleep 2
  done
  echo "Timed out waiting for ${name} at ${base}" >&2
  [[ -z "${log}" ]] || tail -n 120 "${log}" >&2 || true
  [[ ! -s "${body}" ]] || head -c 2000 "${body}" >&2 || true
  return 1
}

sample_process() {
  local pid="$1" file="$2" cpu rss
  while kill -0 "${pid}" 2>/dev/null; do
    read -r cpu rss < <(ps -p "${pid}" -o %cpu=,rss= 2>/dev/null || echo '0 0')
    printf '%s %s\n' "${cpu:-0}" "${rss:-0}" >> "${file}"
    sleep 1
  done
}

worker() {
  local url="$1" end=$((SECONDS + PERF_DURATION_SECONDS)) response
  while (( SECONDS < end )); do
    response="$(curl -sS --max-time 10 -o /dev/null -w '%{http_code} %{time_total}' "${url}" 2>/dev/null || echo '000 10')"
    printf '%s\t%s\n' "${response%% *}" "${response##* }" >> "${REQUESTS}"
  done
}

run_case() {
  local mode="$1" base="$2" endpoint="$3" repetition="$4" pid="$5"
  local case_id="${mode}-r${repetition}-$(echo "${endpoint}" | tr '/?' '__')"
  local requests="${PERF_OUTPUT_DIR}/${case_id}.tsv" samples="${PERF_OUTPUT_DIR}/${case_id}.proc"
  local started finished total successes errors avg p95 error_rate rps cpu rss elapsed sample_pid worker_pid
  local worker_pids=()
  REQUESTS="${requests}"; : > "${requests}"; : > "${samples}"
  sample_process "${pid}" "${samples}" & sample_pid=$!
  started="$(date +%s)"
  for _ in $(seq 1 "${PERF_CONCURRENCY}"); do worker "${base}${endpoint}" & worker_pids+=("$!"); done
  for worker_pid in "${worker_pids[@]}"; do wait "${worker_pid}" || true; done
  finished="$(date +%s)"; kill "${sample_pid}" 2>/dev/null || true
  total="$(awk -F '\t' 'NF>=2{n++} END{print n+0}' "${requests}")"
  successes="$(awk -F '\t' '$1 ~ /^2/{n++} END{print n+0}' "${requests}")"
  errors=$((total - successes)); elapsed=$((finished - started)); (( elapsed > 0 )) || elapsed=1
  rps="$(awk -v n="$total" -v s="$elapsed" 'BEGIN{printf "%.2f",n/s}')"
  avg="$(awk -F '\t' '{sum+=$2} END{printf "%.2f",NR?sum/NR*1000:0}' "${requests}")"
  p95="$(awk -F '\t' '{print $2*1000}' "${requests}" | sort -n | awk '{a[NR]=$1} END{if(NR){i=int((NR*95+99)/100);printf "%.2f",a[i]}else print "0.00"}')"
  error_rate="$(awk -v e="$errors" -v n="$total" 'BEGIN{printf "%.2f",n>0?e*100/n:0}')"
  cpu="$(awk '{sum+=$1} END{printf "%.2f",NR?sum/NR:0}' "${samples}")"
  rss="$(awk '{if($2>max)max=$2} END{printf "%.2f",max/1024}' "${samples}")"
  echo "${mode},${endpoint},${repetition},${PERF_CONCURRENCY},${elapsed},${total},${successes},${errors},${rps},${avg},${p95},${error_rate},${cpu},${rss}" >> "${RESULTS}"
}

endpoints=( '/api/live/rooms?page=1&pageSize=10' '/api/live/rooms/1' '/api/live/rooms/1/like' )
for mode in monolith microservice; do
  base="${PERF_MONOLITH_URL}"; pid="${PERF_MONOLITH_PID}"
  log="${PERF_MONOLITH_LOG}"
  if [[ "${mode}" == microservice ]]; then base="${PERF_MICROSERVICE_URL}"; pid="${PERF_MICROSERVICE_PID}"; log="${PERF_MICROSERVICE_LOG}"; fi
  if [[ -z "${pid}" ]] || ! kill -0 "${pid}" 2>/dev/null; then echo "Missing PID for ${mode}" >&2; exit 1; fi
  wait_ready "${mode}" "${base}" "${pid}" "${log}"
  for repetition in $(seq 1 "${PERF_REPETITIONS}"); do
    for endpoint in "${endpoints[@]}"; do run_case "${mode}" "${base}" "${endpoint}" "${repetition}" "${pid}"; done
  done
done
cat "${RESULTS}"
