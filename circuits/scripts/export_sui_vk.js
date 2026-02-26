#!/usr/bin/env node
/**
 * export_sui_vk.js
 *
 * Converts a snarkjs Groth16 verification key (BN254) into the arkworks
 * compressed serialization format expected by Sui's groth16 module.
 *
 * Sui calls: ark_groth16::VerifyingKey::<Bn254>::deserialize_compressed(bytes)
 *
 * Arkworks compressed format (little-endian):
 *   - G1 compressed: 32 bytes (x in LE, sign bit in MSB of last byte)
 *   - G2 compressed: 64 bytes (x as Fq2: c0||c1 in LE, sign bit in MSB of last byte)
 *   - VK layout:
 *       alpha_g1 (32 bytes) ||
 *       beta_g2 (64 bytes) ||
 *       gamma_g2 (64 bytes) ||
 *       delta_g2 (64 bytes) ||
 *       gamma_abc_g1_len (8 bytes, u64 LE) ||
 *       gamma_abc_g1[0] (32 bytes) || gamma_abc_g1[1] (32 bytes) || ...
 *
 * Usage:
 *   node scripts/export_sui_vk.js identity_registration
 */

const fs = require("fs");
const path = require("path");

const FIELD_SIZE = 32;

// BN254 base field modulus
const P = 21888242871839275222246405745257275088696311157297823662689037894645226208583n;
const HALF_P = (P - 1n) / 2n;

/**
 * Convert a decimal string to a little-endian byte array.
 */
function decimalToBytesLE(decStr, numBytes) {
  let n = BigInt(decStr);
  const bytes = new Uint8Array(numBytes);
  for (let i = 0; i < numBytes; i++) {
    bytes[i] = Number(n & 0xffn);
    n >>= 8n;
  }
  return bytes;
}

/**
 * Check if a field element is "negative" (greater than (p-1)/2).
 */
function isNegative(decStr) {
  return BigInt(decStr) > HALF_P;
}

/**
 * Compress a G1 affine point [x, y, "1"] to 32 bytes (arkworks format).
 * x is serialized as LE, and if y is "negative" (> half_p), bit 7 of
 * the last byte is set.
 */
function compressG1(point) {
  const x = BigInt(point[0]);
  const y = BigInt(point[1]);

  const bytes = decimalToBytesLE(point[0], FIELD_SIZE);

  // Set the sign flag (bit 7 of the last byte) if y > (p-1)/2
  if (y > HALF_P) {
    bytes[FIELD_SIZE - 1] |= 0x80;
  }

  return bytes;
}

/**
 * Compress a G2 affine point [[c0,c1],[c0,c1],["1","0"]] to 64 bytes.
 * snarkjs stores Fq2 as [c0, c1] (same order as arkworks).
 * Arkworks serializes Fq2 as c0 || c1, each in LE.
 * Sign flag is in bit 7 of byte[63].
 */
function compressG2(point) {
  // snarkjs: point[0] = [c0, c1] for x, point[1] = [c0, c1] for y
  const x_c0 = point[0][0];
  const x_c1 = point[0][1];
  const y_c0 = BigInt(point[1][0]);
  const y_c1 = BigInt(point[1][1]);

  const bytes = new Uint8Array(FIELD_SIZE * 2);
  // Fq2 = c0 || c1 in LE
  const c0Bytes = decimalToBytesLE(x_c0, FIELD_SIZE);
  const c1Bytes = decimalToBytesLE(x_c1, FIELD_SIZE);
  bytes.set(c0Bytes, 0);
  bytes.set(c1Bytes, FIELD_SIZE);

  // Determine y sign for Fq2:
  // If y.c1 != 0: sign is based on y.c1
  // If y.c1 == 0: sign is based on y.c0
  let yIsNegative = false;
  if (y_c1 !== 0n) {
    yIsNegative = y_c1 > HALF_P;
  } else {
    yIsNegative = y_c0 > HALF_P;
  }

  if (yIsNegative) {
    bytes[FIELD_SIZE * 2 - 1] |= 0x80;
  }

  return bytes;
}

/**
 * Encode a u64 as 8 bytes little-endian.
 */
function u64LE(value) {
  const bytes = new Uint8Array(8);
  let n = BigInt(value);
  for (let i = 0; i < 8; i++) {
    bytes[i] = Number(n & 0xffn);
    n >>= 8n;
  }
  return bytes;
}

function toHex(bytes) {
  return Array.from(bytes)
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function main() {
  const circuitName = process.argv[2];

  if (!circuitName) {
    console.error("Usage: node scripts/export_sui_vk.js <circuit_name>");
    process.exit(1);
  }

  const vkPath = path.join("build", `${circuitName}_verification_key.json`);
  if (!fs.existsSync(vkPath)) {
    console.error(`Verification key not found: ${vkPath}`);
    process.exit(1);
  }

  console.log(`Reading verification key from: ${vkPath}`);
  const vk = JSON.parse(fs.readFileSync(vkPath, "utf-8"));

  // Compress all curve points
  const alpha_g1 = compressG1(vk.vk_alpha_1);       // 32 bytes
  const beta_g2 = compressG2(vk.vk_beta_2);          // 64 bytes
  const gamma_g2 = compressG2(vk.vk_gamma_2);        // 64 bytes
  const delta_g2 = compressG2(vk.vk_delta_2);        // 64 bytes

  // IC points
  const icPoints = vk.IC.map((point) => compressG1(point));
  const numIc = vk.IC.length;
  const numIcBytes = u64LE(numIc);                    // 8 bytes (u64 LE)

  // Total size
  const totalSize = 32 + 64 + 64 + 64 + 8 + numIc * 32;
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

  // Write binary
  const binPath = path.join("build", `${circuitName}_sui_vk.bin`);
  fs.writeFileSync(binPath, Buffer.from(vkBytes));
  console.log(`Binary VK written to: ${binPath} (${totalSize} bytes)`);

  // Write JSON
  const suiVk = {
    curve: "BN254",
    circuit: circuitName,
    encoding: "arkworks compressed, little-endian",
    total_size_bytes: totalSize,
    num_public_inputs: numIc - 1,
    vk_bytes_hex: toHex(vkBytes),
  };

  const jsonPath = path.join("build", `${circuitName}_sui_vk.json`);
  fs.writeFileSync(jsonPath, JSON.stringify(suiVk, null, 2));
  console.log(`JSON VK written to: ${jsonPath}`);

  console.log(`\n--- Sui VK Summary ---`);
  console.log(`  Encoding:        arkworks compressed (LE)`);
  console.log(`  Circuit:         ${circuitName}`);
  console.log(`  Public inputs:   ${numIc - 1}`);
  console.log(`  Total VK bytes:  ${totalSize}`);
  console.log(`\nUse vk_bytes_hex from ${jsonPath} to create the on-chain VerificationKey.`);
}

main();
