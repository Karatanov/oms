#!/usr/bin/env bash
set -euo pipefail
backup="${1:?verified backup directory}"
case "$backup" in /docker/oms/backups/registry-*) ;; *) exit 2 ;; esac
test -f "$backup/precision-trial-verification.txt"
script_dir="$(cd "$(dirname "$0")" && pwd)"
bash "$script_dir/apply-registry-amount-repair.sh" "$backup"
