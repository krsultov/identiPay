import { sha256 } from "@noble/hashes/sha256";
import { bytesToHex, hexToBytes } from "@noble/hashes/utils";

export function sha256Hash(data: Uint8Array): Uint8Array {
  return sha256(data);
}

export function sha256Hex(data: Uint8Array): string {
  return bytesToHex(sha256(data));
}

export function hashApiKey(apiKey: string): string {
  return sha256Hex(new TextEncoder().encode(apiKey));
}

export function randomBytes(length: number): Uint8Array {
  const bytes = new Uint8Array(length);
  crypto.getRandomValues(bytes);
  return bytes;
}

export function generateApiKey(): string {
  return bytesToHex(randomBytes(32));
}

export { bytesToHex, hexToBytes };
