#!/usr/bin/env bash
# compile.sh - Compile a circom circuit and generate proving/verification keys
#
# Usage: bash scripts/compile.sh <circuit_name>
# Example: bash scripts/compile.sh identity_registration

set -euo pipefail

CIRCUIT_NAME="${1:?Usage: compile.sh <circuit_name>}"
BUILD_DIR="build"
PTAU_FILE="${BUILD_DIR}/powersOfTau28_hez_final_14.ptau"
PTAU_URL="https://storage.googleapis.com/zkevm/ptau/powersOfTau28_hez_final_14.ptau"

# Use node directly to avoid npx startup overhead on Windows
snarkjs() {
    node node_modules/snarkjs/cli.js "$@"
}

echo "============================================="
echo " Compiling circuit: ${CIRCUIT_NAME}"
echo "============================================="

mkdir -p "${BUILD_DIR}"

# Step 1: Compile circom -> R1CS + WASM + SYM
echo ""
echo "[1/4] Compiling ${CIRCUIT_NAME}.circom ..."
circom "${CIRCUIT_NAME}.circom" \
    --r1cs \
    --wasm \
    --sym \
    -o "${BUILD_DIR}/"

echo "  R1CS: ${BUILD_DIR}/${CIRCUIT_NAME}.r1cs"
echo "  WASM: ${BUILD_DIR}/${CIRCUIT_NAME}_js/${CIRCUIT_NAME}.wasm"

# Step 2: Download Powers of Tau (if needed)
echo ""
echo "[2/4] Checking Powers of Tau file ..."
if [ ! -f "${PTAU_FILE}" ]; then
    echo "  Downloading powersOfTau28_hez_final_14.ptau (~18MB) ..."
    curl -L -o "${PTAU_FILE}" "${PTAU_URL}"
else
    echo "  Found existing ptau file."
fi

# Step 3: Groth16 setup + contribute + beacon
echo ""
echo "[3/4] Running Groth16 setup ..."
snarkjs groth16 setup \
    "${BUILD_DIR}/${CIRCUIT_NAME}.r1cs" \
    "${PTAU_FILE}" \
    "${BUILD_DIR}/${CIRCUIT_NAME}_0000.zkey"

echo "  Contributing to ceremony ..."
snarkjs zkey contribute \
    "${BUILD_DIR}/${CIRCUIT_NAME}_0000.zkey" \
    "${BUILD_DIR}/${CIRCUIT_NAME}_0001.zkey" \
    --name="identipay dev" \
    -e="identipay-dev-entropy"

echo "  Applying beacon ..."
snarkjs zkey beacon \
    "${BUILD_DIR}/${CIRCUIT_NAME}_0001.zkey" \
    "${BUILD_DIR}/${CIRCUIT_NAME}_final.zkey" \
    0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20 \
    10 \
    -n="Final beacon"

# Step 4: Export verification key
echo ""
echo "[4/4] Exporting verification key ..."
snarkjs zkey export verificationkey \
    "${BUILD_DIR}/${CIRCUIT_NAME}_final.zkey" \
    "${BUILD_DIR}/${CIRCUIT_NAME}_verification_key.json"

# Cleanup intermediate files
rm -f "${BUILD_DIR}/${CIRCUIT_NAME}_0000.zkey" "${BUILD_DIR}/${CIRCUIT_NAME}_0001.zkey"

echo ""
echo "============================================="
echo " Done! Artifacts:"
echo "  WASM:  ${BUILD_DIR}/${CIRCUIT_NAME}_js/${CIRCUIT_NAME}.wasm"
echo "  zkey:  ${BUILD_DIR}/${CIRCUIT_NAME}_final.zkey"
echo "  VK:    ${BUILD_DIR}/${CIRCUIT_NAME}_verification_key.json"
echo "============================================="
