import { SuiClient } from "@mysten/sui/client";
import { Transaction } from "@mysten/sui/transactions";
import { Ed25519Keypair } from "@mysten/sui/keypairs/ed25519";
import { fromBase64 } from "@mysten/sui/utils";
import { hexToBytes, bytesToHex } from "@noble/hashes/utils";
import { SuiError } from "../errors/index.ts";

export interface SuiServiceConfig {
  rpcUrl: string;
  packageId: string;
  trustRegistryId: string;
  metaRegistryId: string;
  settlementStateId: string;
  adminSecretKey: string;
  verificationKeyId: string;
}

export interface MerchantOnChainParams {
  did: string;
  name: string;
  suiAddress: string;
  publicKey: string; // hex
  hostname: string;
}

export interface ResolvedName {
  spendPubkey: string;
  viewPubkey: string;
}

export type SettlementEventCallback = (event: {
  intentHash: string;
  merchant: string;
  amount: string;
  receiptId: string;
  warrantyId: string | null;
  buyerStealthAddress: string;
}) => void;

export type AnnouncementEventCallback = (event: {
  ephemeralPubkey: string;
  viewTag: number;
  stealthAddress: string;
  metadata: string | null;
  txDigest: string;
  timestamp: string;
}) => void;

export class SuiService {
  private client: SuiClient;
  private adminKeypair: Ed25519Keypair;
  private config: SuiServiceConfig;

  constructor(config: SuiServiceConfig) {
    this.config = config;
    this.client = new SuiClient({ url: config.rpcUrl });
    // Accepts bech32 suiprivkey1... string directly
    this.adminKeypair = Ed25519Keypair.fromSecretKey(config.adminSecretKey);
  }

  /**
   * Look up a merchant in the on-chain TrustRegistry.
   */
  async lookupMerchant(did: string): Promise<MerchantOnChainParams | null> {
    try {
      const result = await this.client.devInspectTransactionBlock({
        transactionBlock: (() => {
          const tx = new Transaction();
          tx.moveCall({
            target: `${this.config.packageId}::trust_registry::lookup_merchant`,
            arguments: [
              tx.object(this.config.trustRegistryId),
              tx.pure.string(did),
            ],
          });
          return tx;
        })(),
        sender: this.adminKeypair.getPublicKey().toSuiAddress(),
      });

      if (!result.results?.[0]?.returnValues) return null;

      // Parse BCS-encoded MerchantEntry
      const returnValues = result.results[0].returnValues;
      if (!returnValues || returnValues.length === 0) return null;

      // For devInspect, we get raw bytes - return a simplified result
      // In production, parse BCS properly
      return null; // Fallback: merchant not found via devInspect
    } catch {
      return null;
    }
  }

  /**
   * Resolve a name from the on-chain MetaAddressRegistry.
   * Returns ONLY (spend_pubkey, view_pubkey) -- NEVER a Sui address.
   */
  async resolveName(name: string): Promise<ResolvedName | null> {
    try {
      const result = await this.client.devInspectTransactionBlock({
        transactionBlock: (() => {
          const tx = new Transaction();
          tx.moveCall({
            target: `${this.config.packageId}::meta_address_registry::resolve_name`,
            arguments: [
              tx.object(this.config.metaRegistryId),
              tx.pure.string(name),
            ],
          });
          return tx;
        })(),
        sender: this.adminKeypair.getPublicKey().toSuiAddress(),
      });

      if (!result.results?.[0]?.returnValues) return null;
      const returnValues = result.results[0].returnValues;
      if (!returnValues || returnValues.length < 2) return null;

      // Parse the two vector<u8> return values (spend_pubkey, view_pubkey)
      const spendPubkey = bytesToHex(new Uint8Array(returnValues[0][0] as number[]));
      const viewPubkey = bytesToHex(new Uint8Array(returnValues[1][0] as number[]));

      return { spendPubkey, viewPubkey };
    } catch {
      return null;
    }
  }

  /**
   * Register a merchant on-chain. Admin-only operation.
   */
  async registerMerchantOnChain(params: MerchantOnChainParams): Promise<string> {
    try {
      const tx = new Transaction();
      tx.moveCall({
        target: `${this.config.packageId}::trust_registry::register_merchant`,
        arguments: [
          tx.object(this.config.trustRegistryId),
          tx.pure.string(params.did),
          tx.pure.string(params.name),
          tx.pure.address(params.suiAddress),
          tx.pure.vector('u8', Array.from(hexToBytes(params.publicKey))),
          tx.pure.string(params.hostname),
        ],
      });

      const result = await this.client.signAndExecuteTransaction({
        transaction: tx,
        signer: this.adminKeypair,
        options: { showEffects: true },
      });

      if (result.effects?.status?.status !== "success") {
        throw new SuiError(
          "Merchant registration failed on-chain",
          result.effects?.status,
        );
      }

      return result.digest;
    } catch (error) {
      if (error instanceof SuiError) throw error;
      throw new SuiError("Failed to register merchant on-chain", {
        message: (error as Error).message,
      });
    }
  }

  /**
   * Build and execute a name registration PTB.
   * The admin key pays for gas. The MetaAddressEntry is transferred to admin
   * (the wallet's public keys are stored in the entry for stealth address derivation).
   */
  async registerName(params: {
    name: string;
    spendPubkey: string;
    viewPubkey: string;
    identityCommitment: string;
    zkProof: string;
    zkPublicInputs: string;
  }): Promise<string> {
    try {
      const tx = new Transaction();
      tx.moveCall({
        target: `${this.config.packageId}::meta_address_registry::register_name`,
        arguments: [
          tx.object(this.config.metaRegistryId),
          tx.object(this.config.verificationKeyId),
          tx.pure.string(params.name),
          tx.pure.vector('u8', Array.from(hexToBytes(params.spendPubkey))),
          tx.pure.vector('u8', Array.from(hexToBytes(params.viewPubkey))),
          tx.pure.vector('u8', Array.from(hexToBytes(params.identityCommitment))),
          tx.pure.vector('u8', Array.from(hexToBytes(params.zkProof))),
          tx.pure.vector('u8', Array.from(hexToBytes(params.zkPublicInputs))),
        ],
      });

      const result = await this.client.signAndExecuteTransaction({
        transaction: tx,
        signer: this.adminKeypair,
        options: { showEffects: true },
      });

      if (result.effects?.status?.status !== "success") {
        throw new SuiError(
          "Name registration failed on-chain",
          result.effects?.status,
        );
      }

      return result.digest;
    } catch (error) {
      if (error instanceof SuiError) throw error;
      throw new SuiError("Failed to register name on-chain", {
        message: (error as Error).message,
      });
    }
  }

  /**
   * Sponsor and submit a wallet-signed transaction.
   * The backend co-signs for gas without becoming the sender.
   * ctx.sender() remains the wallet's address.
   */
  async sponsorAndSubmitTx(signedTxBytes: string): Promise<string> {
    try {
      // The wallet has already signed the tx. We add gas sponsorship.
      const txBytes = fromBase64(signedTxBytes);

      // Parse the transaction to add gas sponsorship
      const tx = Transaction.from(txBytes);

      // Set the admin as gas sponsor
      tx.setSender(tx.getData().sender!);
      tx.setGasOwner(this.adminKeypair.getPublicKey().toSuiAddress());

      const sponsoredTx = await this.client.signAndExecuteTransaction({
        transaction: tx,
        signer: this.adminKeypair,
        options: { showEffects: true },
      });

      if (sponsoredTx.effects?.status?.status !== "success") {
        throw new SuiError("Sponsored transaction failed", sponsoredTx.effects?.status);
      }

      return sponsoredTx.digest;
    } catch (error) {
      if (error instanceof SuiError) throw error;
      throw new SuiError("Failed to sponsor and submit transaction", {
        message: (error as Error).message,
      });
    }
  }

  /**
   * Subscribe to SettlementEvents for real-time settlement tracking.
   */
  async subscribeToSettlementEvents(callback: SettlementEventCallback): Promise<() => void> {
    const eventType = `${this.config.packageId}::settlement::SettlementEvent`;

    const unsubscribe = await this.client.subscribeEvent({
      filter: { MoveEventType: eventType },
      onMessage: (event) => {
        const fields = event.parsedJson as Record<string, unknown>;
        callback({
          intentHash: fields.intent_hash as string,
          merchant: fields.merchant as string,
          amount: String(fields.amount),
          receiptId: fields.receipt_id as string,
          warrantyId: (fields.warranty_id as string) ?? null,
          buyerStealthAddress: fields.buyer_stealth_address as string,
        });
      },
    });

    return unsubscribe;
  }

  /**
   * Subscribe to StealthAnnouncement events for indexing.
   */
  async subscribeToAnnouncementEvents(callback: AnnouncementEventCallback): Promise<() => void> {
    const eventType = `${this.config.packageId}::announcements::StealthAnnouncement`;

    const unsubscribe = await this.client.subscribeEvent({
      filter: { MoveEventType: eventType },
      onMessage: (event) => {
        const fields = event.parsedJson as Record<string, unknown>;
        callback({
          ephemeralPubkey: fields.ephemeral_pubkey as string,
          viewTag: Number(fields.view_tag),
          stealthAddress: fields.stealth_address as string,
          metadata: (fields.metadata as string) ?? null,
          txDigest: event.id.txDigest,
          timestamp: String(event.timestampMs),
        });
      },
    });

    return unsubscribe;
  }

  /**
   * Query historical events for backfill.
   */
  async queryEvents(
    eventType: string,
    cursor?: { txDigest: string; eventSeq: string },
    limit = 100,
  ) {
    return await this.client.queryEvents({
      query: { MoveEventType: `${this.config.packageId}::${eventType}` },
      cursor: cursor ?? null,
      limit,
      order: "ascending",
    });
  }

  getAdminAddress(): string {
    return this.adminKeypair.getPublicKey().toSuiAddress();
  }
}
