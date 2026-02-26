function env(key: string, fallback?: string): string {
  const value = Deno.env.get(key) ?? fallback;
  if (value === undefined) {
    throw new Error(`Missing required environment variable: ${key}`);
  }
  return value;
}

export const config = {
  port: parseInt(env("PORT", "8000")),
  host: env("HOST", "0.0.0.0"),
  databaseUrl: env("DATABASE_URL"),
  suiRpcUrl: env("SUI_RPC_URL", "https://fullnode.testnet.sui.io:443"),
  packageId: env("PACKAGE_ID"),
  trustRegistryId: env("TRUST_REGISTRY_ID"),
  metaRegistryId: env("META_REGISTRY_ID"),
  settlementStateId: env("SETTLEMENT_STATE_ID"),
  adminSecretKey: env("ADMIN_SECRET_KEY"),
  verificationKeyId: env("VERIFICATION_KEY_ID"),
} as const;

export type Config = typeof config;
