#!/bin/bash
set -e

# Inputs
ARTIFACT_NAME="${1}"
PLATFORM="${2}"

# Find all matching native binaries
# Start with empty outputs
echo "Processing binaries matching: ${ARTIFACT_NAME}"

find . -path '*/target/*' -type f -name "${ARTIFACT_NAME}" -perm -u+x -print0 | while IFS= read -r -d '' BINARY; do
  echo "Found binary: $BINARY"
  
  # Size Check
  if [[ "$OSTYPE" == "darwin"* ]]; then
    SIZE=$(stat -f%z "$BINARY")
  else
    SIZE=$(stat -c%s "$BINARY")
  fi
  SIZE_MB=$(echo "scale=2; $SIZE / 1024 / 1024" | bc)
  echo "Binary size: ${SIZE_MB}MB"
  
  # Rename and Checksum
  BINARY_DIR=$(dirname "$BINARY")
  BINARY_NAME=$(basename "$BINARY")
  RENAMED="${BINARY_NAME}-${PLATFORM}"
  
  cd "$BINARY_DIR"
  if [[ "$BINARY_NAME" != *"${PLATFORM}" ]]; then
    mv "$BINARY_NAME" "$RENAMED"
    BINARY_NAME="$RENAMED"
  fi
  
  if [[ "$OSTYPE" == "darwin"* ]]; then
    shasum -a 256 "$BINARY_NAME" > "${BINARY_NAME}.sha256"
  else
    sha256sum "$BINARY_NAME" > "${BINARY_NAME}.sha256"
  fi
  
  # Return to root
  cd - > /dev/null
done
