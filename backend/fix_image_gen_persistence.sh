#!/bin/bash
# One-time script: download Seedream images from URLs and persist locally.
# Usage: bash fix_image_gen_persistence.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$SCRIPT_DIR/.env"

if [ -f "$ENV_FILE" ]; then
  set -a; source "$ENV_FILE"; set +a
else
  echo "ERROR: .env not found at $ENV_FILE"
  exit 1
fi

STORAGE_BASE="$SCRIPT_DIR/storage/creation-adapt"
mkdir -p "$STORAGE_BASE"

QUERY="SELECT id, match_id, project_id, version_id, segment_index, image_gen_url
FROM slot_match_result
WHERE image_gen_status = 'COMPLETED'
  AND image_gen_url IS NOT NULL
  AND image_gen_url != ''
  AND (adapted_file_path IS NULL OR adapted_file_path LIKE 'http%')
  AND deleted_at IS NULL;"

echo "=== Querying records to fix ==="
RESULT=$(mysql -h"${DB_HOST}" -P"${DB_PORT}" -u"${DB_USERNAME}" -p"${DB_PASSWORD}" \
  -D"${DB_NAME}" -N -s --raw -e "$QUERY" 2>&1) || {
  echo "ERROR: MySQL query failed: $RESULT"
  exit 1
}

if [ -z "$RESULT" ]; then
  echo "No records to fix. All COMPLETED records already have local paths."
  exit 0
fi

FIXED=0
SKIPPED=0
FAILED=0

while IFS=$'\t' read -r id match_id project_id version_id segment_index image_gen_url; do
  echo ""
  echo "--- [$((FIXED + SKIPPED + FAILED + 1))] match_id=$match_id seg=$segment_index ---"

  OUTPUT_DIR="$STORAGE_BASE/$project_id/$version_id"
  LOCAL_FILE="$OUTPUT_DIR/img_gen_seg_$(printf '%03d' "$segment_index").png"

  if [ -f "$LOCAL_FILE" ]; then
    echo "SKIP: local file already exists at $LOCAL_FILE"
    mysql -h"${DB_HOST}" -P"${DB_PORT}" -u"${DB_USERNAME}" -p"${DB_PASSWORD}" \
      -D"${DB_NAME}" -e \
      "UPDATE slot_match_result SET adapted_file_path = '$LOCAL_FILE', image_gen_error_message = NULL WHERE id = $id;" 2>/dev/null
    SKIPPED=$((SKIPPED + 1))
    continue
  fi

  echo "Download: $image_gen_url -> $LOCAL_FILE"
  mkdir -p "$OUTPUT_DIR"

  if curl -fSL --connect-timeout 15 --max-time 120 -o "$LOCAL_FILE" "$image_gen_url" 2>&1; then
    FILE_SIZE=$(wc -c < "$LOCAL_FILE" | tr -d ' ')
    echo "OK: downloaded ${FILE_SIZE} bytes"

    mysql -h"${DB_HOST}" -P"${DB_PORT}" -u"${DB_USERNAME}" -p"${DB_PASSWORD}" \
      -D"${DB_NAME}" -e \
      "UPDATE slot_match_result SET adapted_file_path = '$LOCAL_FILE', image_gen_error_message = NULL WHERE id = $id;" 2>/dev/null || {
      echo "WARN: DB update failed for id=$id"
    }
    FIXED=$((FIXED + 1))
  else
    echo "FAIL: download failed for $image_gen_url"
    FAILED=$((FAILED + 1))
  fi
done <<< "$RESULT"

echo ""
echo "=== Done: fixed=$FIXED skipped=$SKIPPED failed=$FAILED ==="
