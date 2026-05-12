#!/usr/bin/env bash
set -u

usage() {
  cat <<'USAGE'
Usage:
  bash docs/fallback-ip-curl-example.sh --base-url URL --fallback-ip IP [options]

Required:
  --base-url URL          Server root URL, for example https://monitor.example.com
  --fallback-ip IP        Fallback address for the URL host. Can be repeated.
  --fallback-ips "LIST"   Comma or space separated fallback addresses.

Options:
  --endpoint PATH         Monitor endpoint path. Default: /monitor/api
  --token TOKEN           Send TOKEN as X-Monitor-Token.
  --basic-auth USER:PASS  Send HTTP Basic Auth credentials.
  --connect-timeout SEC   Curl connection timeout. Default: 8
  --max-time SEC          Curl total request timeout. Default: 20
  -h, --help              Show this help.

Example:
  bash docs/fallback-ip-curl-example.sh \
    --base-url https://monitor.example.com \
    --fallback-ip 192.168.1.20 \
    --fallback-ip 10.0.0.5 \
    --token secret

The fallback keeps the URL host as monitor.example.com and only overrides DNS
with curl --resolve. For HTTPS, curl still sends SNI for that host and verifies
the certificate against that host.
USAGE
}

die() {
  printf '%s\n' "$1" >&2
  printf '\n' >&2
  usage >&2
  exit 2
}

add_fallback_ips() {
  local values ip
  values="${1//,/ }"
  for ip in $values; do
    fallback_list+=("$ip")
  done
}

base_url=""
endpoint="/monitor/api"
token=""
basic_auth=""
connect_timeout="8"
max_time="20"
fallback_list=()

while (($# > 0)); do
  case "$1" in
    --base-url)
      (($# >= 2)) || die "--base-url requires a value"
      base_url="$2"
      shift 2
      ;;
    --base-url=*)
      base_url="${1#*=}"
      shift
      ;;
    --fallback-ip)
      (($# >= 2)) || die "--fallback-ip requires a value"
      add_fallback_ips "$2"
      shift 2
      ;;
    --fallback-ip=*)
      add_fallback_ips "${1#*=}"
      shift
      ;;
    --fallback-ips)
      (($# >= 2)) || die "--fallback-ips requires a value"
      add_fallback_ips "$2"
      shift 2
      ;;
    --fallback-ips=*)
      add_fallback_ips "${1#*=}"
      shift
      ;;
    --endpoint)
      (($# >= 2)) || die "--endpoint requires a value"
      endpoint="$2"
      shift 2
      ;;
    --endpoint=*)
      endpoint="${1#*=}"
      shift
      ;;
    --token)
      (($# >= 2)) || die "--token requires a value"
      token="$2"
      shift 2
      ;;
    --token=*)
      token="${1#*=}"
      shift
      ;;
    --basic-auth)
      (($# >= 2)) || die "--basic-auth requires a value"
      basic_auth="$2"
      shift 2
      ;;
    --basic-auth=*)
      basic_auth="${1#*=}"
      shift
      ;;
    --connect-timeout)
      (($# >= 2)) || die "--connect-timeout requires a value"
      connect_timeout="$2"
      shift 2
      ;;
    --connect-timeout=*)
      connect_timeout="${1#*=}"
      shift
      ;;
    --max-time)
      (($# >= 2)) || die "--max-time requires a value"
      max_time="$2"
      shift 2
      ;;
    --max-time=*)
      max_time="${1#*=}"
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "Unknown argument: $1"
      ;;
  esac
done

[[ -n "$base_url" ]] || die "--base-url is required"
((${#fallback_list[@]} > 0)) || die "At least one --fallback-ip or --fallback-ips value is required"

base_url="${base_url%/}"
[[ "$endpoint" == /* ]] || endpoint="/$endpoint"
url="${base_url}${endpoint}"

if [[ "$base_url" =~ ^([A-Za-z][A-Za-z0-9+.-]*)://([^/]+) ]]; then
  scheme="${BASH_REMATCH[1]}"
  authority="${BASH_REMATCH[2]}"
else
  die "--base-url must be an http or https URL"
fi

if [[ "$authority" == *"@"* ]]; then
  die "Put Basic Auth in --basic-auth USER:PASS, not in the URL"
fi

if [[ "$authority" =~ ^\[([^]]+)\](:([0-9]+))?$ ]]; then
  host="${BASH_REMATCH[1]}"
  port="${BASH_REMATCH[3]:-}"
elif [[ "$authority" =~ ^([^:]+)(:([0-9]+))?$ ]]; then
  host="${BASH_REMATCH[1]}"
  port="${BASH_REMATCH[3]:-}"
else
  die "Could not parse host and port from $authority"
fi

case "$scheme" in
  [Hh][Tt][Tt][Pp][Ss]) port="${port:-443}" ;;
  [Hh][Tt][Tt][Pp]) port="${port:-80}" ;;
  *) die "Only http and https URLs are supported" ;;
esac

curl_args=(
  --silent
  --show-error
  --location
  --connect-timeout "$connect_timeout"
  --max-time "$max_time"
  --header "Accept: application/json"
)

if [[ -n "$token" ]]; then
  curl_args+=(--header "X-Monitor-Token: $token")
fi

if [[ -n "$basic_auth" ]]; then
  curl_args+=(--user "$basic_auth")
fi

request_once() {
  local body_file http_code rc

  body_file="$(mktemp)"
  http_code="$(curl "${curl_args[@]}" "$@" --output "$body_file" --write-out '%{http_code}' "$url")"
  rc=$?

  if ((rc != 0)); then
    rm -f "$body_file"
    return "$rc"
  fi

  cat "$body_file"
  rm -f "$body_file"

  if [[ "$http_code" == 2* ]]; then
    return 0
  fi

  printf '\nHTTP %s from monitor endpoint\n' "$http_code" >&2
  return 22
}

printf 'Trying normal DNS for %s\n' "$host" >&2
if request_once; then
  exit 0
fi

rc=$?
if ((rc == 22)); then
  exit "$rc"
fi

printf 'Normal request failed before a usable HTTP response; trying fallback IPs\n' >&2
last_rc="$rc"

for ip in "${fallback_list[@]}"; do
  [[ -n "$ip" ]] || continue
  resolve_ip="$ip"
  if [[ "$resolve_ip" == *:* && "$resolve_ip" != \[*\] ]]; then
    resolve_ip="[$resolve_ip]"
  fi

  printf 'Trying %s via --resolve %s:%s:%s\n' "$ip" "$host" "$port" "$resolve_ip" >&2
  if request_once --resolve "${host}:${port}:${resolve_ip}"; then
    exit 0
  fi
  last_rc=$?
done

exit "$last_rc"
