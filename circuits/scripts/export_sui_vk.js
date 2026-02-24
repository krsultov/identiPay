#!/usr/bin/env node
/**
 * export_sui_vk.js
 *
 * Converts a snarkjs Groth16 verification key (BN254/BN128 curve) into the
 * binary format expected by sui::groth16::prepare_verifying_key and
 * sui::groth16::verify_groth16_proof on the Sui blockchain.
 *
 * Usage:
 *   node scripts/export_sui_vk.js <circuit_name>
 *   node scripts/export_sui_vk.js identity_registration
 *
 * Output:
 *   build/<circuit_name>_sui_vk.json   - JSON with hex-encoded byte arrays
 *   build/<circuit_name>_sui_vk.bin    - Raw binary VK for on-chain upload
 *
 * The Sui groth16 module expects BN254 curve points serialized as:
 *   - G1 points: 64 bytes (32 bytes x, 32 bytes y) in big-endian
 *   - G2 points: 128 bytes (2x 32 bytes for x, 2x 32 bytes for y) in big-endian
 *   - Verification key layout:
 *       alpha_g1 (64 bytes) ||
 *       beta_g2 (128 bytes) ||
 *       gamma_g2 (128 bytes) ||
 *       delta_g2 (128 bytes) ||
 *       num_ic (4 bytes, little-endian u32) ||
 *       ic[0] (64 bytes) || ic[1] (64 bytes) || ...
 */

const fs = require("fs");
const path = require("path");

// BN254 field element size in bytes
const FIELD_SIZE = 32;

/**
 * Convert a decimal string to a big-endian byte array of fixed length.
 */
function decimalToBytes(decStr, numBytes) {
  let n = BigInt(decStr);
  const bytes = new Uint8Array(numBytes);
  for (let i = numBytes - 1; i >= 0; i--) {
    bytes[i] = Number(n & 0xffn);
    n >>= 8n;
  }
  if (n > 0n) {
    throw new Error(`Value ${decStr} exceeds ${numBytes} bytes`);
  }
  return bytes;
}

/**
 * Serialize a G1 point [x, y] to 64 bytes (big-endian).
 */
function serializeG1(point) {
  const x = decimalToBytes(point[0], FIELD_SIZE);
  const y = decimalToBytes(point[1], FIELD_SIZE);
  const buf = new Uint8Array(FIELD_SIZE * 2);
  buf.set(x, 0);
  buf.set(y, FIELD_SIZE);
  return buf;
}

/**
 * Serialize a G2 point [[x1, x0], [y1, y0]] to 128 bytes (big-endian).
 * Note: snarkjs stores G2 x-coordinates as [x1, x0] (high, low).
 */
function serializeG2(point) {
  const x0 = decimalToBytes(point[0][1], FIELD_SIZE);
  const x1 = decimalToBytes(point[0][0], FIELD_SIZE);
  const y0 = decimalToBytes(point[1][1], FIELD_SIZE);
  const y1 = decimalToBytes(point[1][0], FIELD_SIZE);
  const buf = new Uint8Array(FIELD_SIZE * 4);
  // Sui expects: x0 || x1 || y0 || y1
  buf.set(x0, 0);
  buf.set(x1, FIELD_SIZE);
  buf.set(y0, FIELD_SIZE * 2);
  buf.set(y1, FIELD_SIZE * 3);
  return buf;
}

/**
 * Convert a Uint8Array to a hex string.
 */
function toHex(bytes) {
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function main() {
  const circuitName = process.argv[2];

  if (!circuitName) {
    console.error("Usage: node scripts/export_sui_vk.js <circuit_name>");
    console.error("Example: node scripts/export_sui_vk.js identity_registration");
    process.exit(1);
  }

  const vkPath = path.join("build", `${circuitName}_verification_key.json`);

  if (!fs.existsSync(vkPath)) {
    console.error(`Verification key not found: ${vkPath}`);
    console.error("Run the compile script first: npm run compile:<circuit>");
    process.exit(1);
  }

  console.log(`Reading verification key from: ${vkPath}`);
  const vk = JSON.parse(fs.readFileSync(vkPath, "utf-8"));

  // Validate curve
  if (vk.curve && vk.curve.toLowerCase() !== "bn128") {
    console.warn(
      `Warning: Expected BN128/BN254 curve, got "${vk.curve}". ` +
        "Sui groth16 only supports BN254."
    );
  }

  // Serialize all components
  const alpha_g1 = serializeG1(vk.vk_alpha_1);
  const beta_g2 = serializeG2(vk.vk_beta_2);
  const gamma_g2 = serializeG2(vk.vk_gamma_2);
  const delta_g2 = serializeG2(vk.vk_delta_2);

  // IC points (input commitments)
  const icPoints = vk.IC.map((point) => serializeG1(point));
  const numIc = vk.IC.length;

  // Encode num_ic as 4-byte little-endian u32
  const numIcBytes = new Uint8Array(4);
  numIcBytes[0] = numIc & 0xff;
  numIcBytes[1] = (numIc >> 8) & 0xff;
  numIcBytes[2] = (numIc >> 16) & 0xff;
  numIcBytes[3] = (numIc >> 24) & 0xff;

  // Concatenate all parts into one binary blob
  const totalSize =
    alpha_g1.length +
    beta_g2.length +
    gamma_g2.length +
    delta_g2.length +
    numIcBytes.length +
    icPoints.reduce((sum, p) => sum + p.length, 0);

  const vkBytes = new Uint8Array(totalSize);
  let offset = 0;

  function append(data) {
    vkBytes.set(data, offset);
    offset += data.length;
  }

  append(alpha_g1);
  append(beta_g2);
  append(gamma_g2);
  append(delta_g2);
  append(numIcBytes);
  icPoints.forEach((p) => append(p));

  // Write binary file
  const binPath = path.join("build", `${circuitName}_sui_vk.bin`);
  fs.writeFileSync(binPath, Buffer.from(vkBytes));
  console.log(`Binary VK written to: ${binPath} (${totalSize} bytes)`);

  // Write JSON with hex-encoded components for easy inspection
  const suiVk = {
    curve: "BN254",
    circuit: circuitName,
    total_size_bytes: totalSize,
    num_public_inputs: numIc - 1, // IC[0] is the base, rest are per-input
    vk_bytes_hex: toHex(vkBytes),
    components: {
      alpha_g1: toHex(alpha_g1),
      beta_g2: toHex(beta_g2),
      gamma_g2: toHex(gamma_g2),
      delta_g2: toHex(delta_g2),
      num_ic: numIc,
      ic: icPoints.map((p) => toHex(p)),
    },
    // Sui Move usage example
    sui_usage: `
// In your Sui Move module:
use sui::groth16;

public fun verify_proof(
    vk_bytes: vector<u8>,
    public_inputs_bytes: vector<u8>,
    proof_bytes: vector<u8>,
): bool {
    let curve = groth16::bn254();
    let pvk = groth16::prepare_verifying_key(&curve, &vk_bytes);
    let public_inputs = groth16::public_proof_inputs_from_bytes(public_inputs_bytes);
    let proof = groth16::proof_points_from_bytes(proof_bytes);
    groth16::verify_groth16_proof(&curve, &pvk, &public_inputs, &proof)
}
    `.trim(),
  };

  const jsonPath = path.join("build", `${circuitName}_sui_vk.json`);
  fs.writeFileSync(jsonPath, JSON.stringify(suiVk, null, 2));
  console.log(`JSON VK written to: ${jsonPath}`);

  // Print summary
  console.log("\n--- Sui VK Summary ---");
  console.log(`  Curve:           BN254`);
  console.log(`  Circuit:         ${circuitName}`);
  console.log(`  Public inputs:   ${numIc - 1}`);
  console.log(`  Total VK bytes:  ${totalSize}`);
  console.log(`  VK hex (first 64 chars): ${toHex(vkBytes).substring(0, 64)}...`);
  console.log("");
  console.log(
    "To upload to Sui, use the hex bytes from the JSON file's vk_bytes_hex field."
  );
}

main();
