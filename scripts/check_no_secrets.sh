#!/usr/bin/env bash
set -euo pipefail

# Verifica que no haya secretos versionados en archivos rastreados por git.
# Excluye .env.example

tracked=$(git ls-files)

patterns=(
    'AKIA[0-9A-Z]{16}'
    'aws_secret_access_key'
    'BEGIN RSA PRIVATE KEY'
    'BEGIN OPENSSH PRIVATE KEY'
    'ghp_[A-Za-z0-9]{36}'
    'sk-[A-Za-z0-9]{32,}'
    'postgres://[^[:space:]]+:[^[:space:]]+@'
    'postgresql://[^[:space:]]+:[^[:space:]]+@'
)

fail=0

for file in $tracked; do
    [ "$file" = ".env.example" ] && continue
    [ "$file" = "scripts/check_no_secrets.sh" ] && continue

    # Saltar binarios.
    if file "$file" | grep -q "binary"; then
        continue
    fi

    for pattern in "${patterns[@]}"; do
        if grep -qE "$pattern" "$file" 2>/dev/null; then
            echo "SECRET PATTERN FOUND in $file: $pattern" >&2
            fail=1
        fi
    done
done

if [ "$fail" -ne 0 ]; then
    echo "Versioned secrets detected" >&2
    exit 1
fi

echo "No versioned secrets detected"
