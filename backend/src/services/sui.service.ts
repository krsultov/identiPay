import { SuiClient } from "@mysten/sui/client";
import { Transaction } from "@mysten/sui/transactions";
import { Ed25519Keypair } from "@mysten/sui/keypairs/ed25519";
import { fromBase64, toBase64 } from "@mysten/sui/utils";
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
  ageCheckVkId: string;
  poolSpendVkId: string;
  shieldedPoolId: string;
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
  txDigest: string;
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
   * Find a coin with sufficient balance, merging multiple coins in the PTB
   * if no single coin is large enough but the aggregate total suffices.
   */
  private async prepareCoin(
    tx: Transaction,
    owner: string,
    coinType: string,
    minBalance?: string,
  ): Promise<string> {
    const coins = await this.client.getCoins({ owner, coinType, limit: 50 });
    if (!coins.data || coins.data.length === 0) {
      throw new SuiError(`No ${coinType} coins found at ${owner}`);
    }
    if (minBalance) {
      const needed = BigInt(minBalance);

      // Fast path: a single coin already covers the amount
      const singleCoin = coins.data.find((c) => BigInt(c.balance) >= needed);
      if (singleCoin) {
        return singleCoin.coinObjectId;
      }

      // Slow path: check aggregate and merge within the PTB
      const totalBalance = coins.data.reduce(
        (sum, c) => sum + BigInt(c.balance),
        0n,
      );
      if (totalBalance < needed) {
        throw new SuiError(`Insufficient ${coinType} balance at ${owner}`);
      }

      const primaryCoinId = coins.data[0].coinObjectId;
      if (coins.data.length > 1) {
        tx.mergeCoins(
          tx.object(primaryCoinId),
          coins.data.slice(1).map((c) => tx.object(c.coinObjectId)),
        );
      }
      return primaryCoinId;
    }
    return coins.data[0].coinObjectId;
  }

  /**
   * Build a gas-sponsored P2P send transaction.
   * Constructs a PTB: splitCoins → transferObjects → announce.
   * Admin pays gas; wallet signs as sender.
   */
  async buildSponsoredSend(params: {
    senderAddress: string;
    coinId?: string;
    amount: string;
    recipient: string;
    coinType: string;
    ephemeralPubkey: number[];
    viewTag: number;
  }): Promise<string> {
    try {
      const tx = new Transaction();
      tx.setSender(params.senderAddress);
      tx.setGasOwner(this.adminKeypair.getPublicKey().toSuiAddress());

      // Find the sender's USDC coin if not provided (may merge multiple coins)
      const coinId = params.coinId ??
        await this.prepareCoin(tx, params.senderAddress, params.coinType, params.amount);

      // Split exact amount from the source coin
      const [splitCoin] = tx.splitCoins(tx.object(coinId), [
        tx.pure.u64(params.amount),
      ]);

      // Transfer split coin to recipient stealth address
      tx.transferObjects([splitCoin], params.recipient);

      // Announce stealth address for recipient scanning
      tx.moveCall({
        target: `${this.config.packageId}::announcements::announce`,
        arguments: [
          tx.pure.vector('u8', params.ephemeralPubkey),
          tx.pure.u8(params.viewTag),
          tx.pure.address(params.recipient),
          tx.pure.vector('u8', []), // empty metadata
        ],
      });

      const builtBytes = await tx.build({ client: this.client });
      return toBase64(builtBytes);
    } catch (error) {
      if (error instanceof SuiError) throw error;
      throw new SuiError("Failed to build sponsored send transaction", {
        message: (error as Error).message,
      });
    }
  }

  /**
   * Build a gas-sponsored commerce settlement transaction.
   * Constructs a PTB calling settlement::execute_commerce or execute_commerce_no_zk.
   * The contract handles coin splitting internally via &mut Coin<T> + amount.
   * Admin pays gas; wallet signs as sender.
   */
  async buildSponsoredSettlement(params: {
    senderAddress: string;
    coinId?: string;
    coinType: string;
    amount: string;
    merchantAddress: string;
    buyerStealthAddr: string;
    intentSig: number[];
    intentHash: number[];
    buyerPubkey: number[];
    proposalExpiry: string;
    encryptedPayload: number[];
    payloadNonce: number[];
    ephemeralPubkey: number[];
    encryptedWarrantyTerms: number[];
    warrantyTermsNonce: number[];
    warrantyExpiry: string;
    warrantyTransferable: boolean;
    // Stealth announcement fields (for wallet scanning)
    stealthEphemeralPubkey: number[];
    stealthViewTag: number;
    // ZK fields (present only for age-gated settlements)
    zkProof?: number[];
    zkPublicInputs?: number[];
  }): Promise<string> {
    try {
      const tx = new Transaction();
      tx.setSender(params.senderAddress);
      tx.setGasOwner(this.adminKeypair.getPublicKey().toSuiAddress());

      // Find the sender's coin if not provided (may merge multiple coins)
      const coinId = params.coinId ??
        await this.prepareCoin(tx, params.senderAddress, params.coinType, params.amount);

      const isAgeGated = params.zkProof != null && params.zkPublicInputs != null;
      const target = isAgeGated
        ? `${this.config.packageId}::settlement::execute_commerce`
        : `${this.config.packageId}::settlement::execute_commerce_no_zk`;

      // Build arguments matching the Move function parameter order
      const args = [
        tx.object(this.config.settlementStateId),  // state: &mut SettlementState
        tx.object(coinId),                           // payment: &mut Coin<T>
        tx.pure.u64(params.amount),                  // amount: u64
        tx.pure.address(params.merchantAddress),     // merchant: address
        tx.pure.address(params.buyerStealthAddr),    // buyer_stealth_addr: address
        tx.pure.vector('u8', params.intentSig),      // intent_sig: vector<u8>
        tx.pure.vector('u8', params.intentHash),     // intent_hash: vector<u8>
        tx.pure.vector('u8', params.buyerPubkey),    // buyer_pubkey: vector<u8>
        tx.pure.u64(params.proposalExpiry),          // proposal_expiry: u64
      ];

      // ZK arguments (only for age-gated execute_commerce)
      if (isAgeGated) {
        args.push(
          tx.object(this.config.ageCheckVkId),            // zk_vk: &VerificationKey (age check)
          tx.pure.vector('u8', params.zkProof!),       // zk_proof: vector<u8>
          tx.pure.vector('u8', params.zkPublicInputs!),// zk_public_inputs: vector<u8>
        );
      }

      // Encrypted receipt and warranty
      args.push(
        tx.pure.vector('u8', params.encryptedPayload),       // encrypted_payload
        tx.pure.vector('u8', params.payloadNonce),            // payload_nonce
        tx.pure.vector('u8', params.ephemeralPubkey),         // ephemeral_pubkey
        tx.pure.vector('u8', params.encryptedWarrantyTerms), // encrypted_warranty_terms
        tx.pure.vector('u8', params.warrantyTermsNonce),     // warranty_terms_nonce
        tx.pure.u64(params.warrantyExpiry),                   // warranty_expiry
        tx.pure.bool(params.warrantyTransferable),            // warranty_transferable
      );

      tx.moveCall({
        target,
        typeArguments: [params.coinType],
        arguments: args,
      });

      // Announce stealth address so the buyer's wallet can detect the receipt
      tx.moveCall({
        target: `${this.config.packageId}::announcements::announce`,
        arguments: [
          tx.pure.vector('u8', params.stealthEphemeralPubkey),
          tx.pure.u8(params.stealthViewTag),
          tx.pure.address(params.buyerStealthAddr),
          tx.pure.vector('u8', []), // empty metadata
        ],
      });

      const builtBytes = await tx.build({ client: this.client });
      return toBase64(builtBytes);
    } catch (error) {
      if (error instanceof SuiError) throw error;
      throw new SuiError("Failed to build sponsored settlement transaction", {
        message: (error as Error).message,
      });
    }
  }

  /**
   * Build a gas-sponsored pool deposit transaction.
   * Splits the deposit amount from the sender's coin, then calls
   * shielded_pool::deposit with the note commitment.
   */
  async buildSponsoredPoolDeposit(params: {
    senderAddress: string;
    coinType: string;
    amount: string;
    noteCommitment: number[];
  }): Promise<string> {
    try {
      const tx = new Transaction();
      tx.setSender(params.senderAddress);
      tx.setGasOwner(this.adminKeypair.getPublicKey().toSuiAddress());

      // Find coin (may merge multiple coins in the PTB)
      const coinId = await this.prepareCoin(
        tx,
        params.senderAddress,
        params.coinType,
        params.amount,
      );

      // Split exact amount from the source coin
      const [depositCoin] = tx.splitCoins(tx.object(coinId), [
        tx.pure.u64(params.amount),
      ]);

      // Convert note commitment bytes to u256 — the bytes are big-endian
      // Pad to 32 bytes then encode as BCS u256
      const commitBytes = new Uint8Array(32);
      const src = new Uint8Array(params.noteCommitment);
      commitBytes.set(src.slice(0, Math.min(src.length, 32)), 32 - Math.min(src.length, 32));

      tx.moveCall({
        target: `${this.config.packageId}::shielded_pool::deposit`,
        typeArguments: [params.coinType],
        arguments: [
          tx.object(this.config.shieldedPoolId),
          depositCoin,
          tx.pure.u256(BigInt("0x" + bytesToHex(commitBytes))),
        ],
      });

      const builtBytes = await tx.build({ client: this.client });
      return toBase64(builtBytes);
    } catch (error) {
      if (error instanceof SuiError) throw error;
      throw new SuiError("Failed to build sponsored pool deposit", {
        message: (error as Error).message,
      });
    }
  }

  /**
   * Build a gas-sponsored pool withdraw transaction.
   * Calls shielded_pool::withdraw with the ZK proof and nullifier.
   */
  async buildSponsoredPoolWithdraw(params: {
    senderAddress: string;
    coinType: string;
    amount: string;
    recipient: string;
    nullifier: number[];
    changeCommitment: number[];
    zkProof: number[];
    zkPublicInputs: number[];
  }): Promise<string> {
    try {
      const tx = new Transaction();
      tx.setSender(this.adminKeypair.getPublicKey().toSuiAddress());
      tx.setGasOwner(this.adminKeypair.getPublicKey().toSuiAddress());

      // Convert nullifier and change commitment bytes to u256
      const nullBytes = new Uint8Array(32);
      const nullSrc = new Uint8Array(params.nullifier);
      nullBytes.set(nullSrc.slice(0, Math.min(nullSrc.length, 32)), 32 - Math.min(nullSrc.length, 32));

      const changeBytes = new Uint8Array(32);
      const changeSrc = new Uint8Array(params.changeCommitment);
      changeBytes.set(changeSrc.slice(0, Math.min(changeSrc.length, 32)), 32 - Math.min(changeSrc.length, 32));

      tx.moveCall({
        target: `${this.config.packageId}::shielded_pool::withdraw`,
        typeArguments: [params.coinType],
        arguments: [
          tx.object(this.config.shieldedPoolId),
          tx.object(this.config.poolSpendVkId),
          tx.pure.vector('u8', params.zkProof),
          tx.pure.vector('u8', params.zkPublicInputs),
          tx.pure.u256(BigInt("0x" + bytesToHex(nullBytes))),
          tx.pure.address(params.recipient),
          tx.pure.u64(params.amount),
          tx.pure.u256(BigInt("0x" + bytesToHex(changeBytes))),
        ],
      });

      const builtBytes = await tx.build({ client: this.client });
      return toBase64(builtBytes);
    } catch (error) {
      if (error instanceof SuiError) throw error;
      throw new SuiError("Failed to build sponsored pool withdraw", {
        message: (error as Error).message,
      });
    }
  }

  /**
   * Submit a sponsored transaction with both sender and gas owner signatures.
   * The wallet signs as sender; the backend signs as gas owner.
   * Both signatures are submitted together.
   */
  async submitSponsoredTx(
    txBytes: string,
    senderSignature: string,
  ): Promise<string> {
    try {
      const txData = fromBase64(txBytes);

      // Admin signs as gas owner
      const adminSignature = await this.adminKeypair.signTransaction(txData);

      // Submit with both signatures: [sender, gasOwner]
      const result = await this.client.executeTransactionBlock({
        transactionBlock: txBytes,
        signature: [senderSignature, adminSignature.signature],
        options: { showEffects: true },
      });

      if (result.effects?.status?.status !== "success") {
        throw new SuiError("Sponsored transaction failed", result.effects?.status);
      }

      return result.digest;
    } catch (error) {
      if (error instanceof SuiError) throw error;
      throw new SuiError("Failed to submit sponsored transaction", {
        message: (error as Error).message,
      });
    }
  }

  /**
   * Submit a transaction where admin is both sender and gas owner.
   * Used for pool withdrawals where the recipient has no coins to sign with.
   */
  async submitAdminOnlyTx(txBytes: string): Promise<string> {
    try {
      const txData = fromBase64(txBytes);
      const adminSignature = await this.adminKeypair.signTransaction(txData);

      const result = await this.client.executeTransactionBlock({
        transactionBlock: txBytes,
        signature: [adminSignature.signature],
        options: { showEffects: true },
      });

      if (result.effects?.status?.status !== "success") {
        throw new SuiError("Admin-only transaction failed", result.effects?.status);
      }

      return result.digest;
    } catch (error) {
      if (error instanceof SuiError) throw error;
      throw new SuiError("Failed to submit admin-only transaction", {
        message: (error as Error).message,
      });
    }
  }

  /**
   * Poll for settlement events since the given cursor.
   * Returns parsed events and the next cursor for persistence.
   */
  async pollSettlementEvents(
    cursor: { txDigest: string; eventSeq: string } | null,
    limit = 50,
  ): Promise<{
    events: Array<{
      intentHash: string;
      merchant: string;
      amount: string;
      receiptId: string;
      warrantyId: string | null;
      buyerStealthAddress: string;
      txDigest: string;
    }>;
    nextCursor: { txDigest: string; eventSeq: string } | null;
    hasNextPage: boolean;
  }> {
    const eventType = `${this.config.packageId}::settlement::SettlementEvent`;
    const result = await this.client.queryEvents({
      query: { MoveEventType: eventType },
      cursor,
      limit,
      order: "ascending",
    });

    const events = result.data.map((event) => {
      const fields = event.parsedJson as Record<string, unknown>;
      // intent_hash is vector<u8> on-chain → parsedJson gives number[]
      const intentHashRaw = fields.intent_hash as number[];
      return {
        intentHash: bytesToHex(Uint8Array.from(intentHashRaw)),
        merchant: fields.merchant as string,
        amount: String(fields.amount),
        receiptId: fields.receipt_id as string,
        warrantyId: (fields.warranty_id as string) ?? null,
        buyerStealthAddress: fields.buyer_stealth_address as string,
        txDigest: event.id.txDigest,
      };
    });

    return {
      events,
      nextCursor: result.nextCursor ?? null,
      hasNextPage: result.hasNextPage,
    };
  }

  /**
   * Poll for stealth announcement events since the given cursor.
   * Returns parsed events and the next cursor for persistence.
   */
  async pollAnnouncementEvents(
    cursor: { txDigest: string; eventSeq: string } | null,
    limit = 50,
  ): Promise<{
    events: Array<{
      ephemeralPubkey: string;
      viewTag: number;
      stealthAddress: string;
      metadata: string | null;
      txDigest: string;
      timestamp: string;
    }>;
    nextCursor: { txDigest: string; eventSeq: string } | null;
    hasNextPage: boolean;
  }> {
    const eventType = `${this.config.packageId}::announcements::StealthAnnouncement`;
    const result = await this.client.queryEvents({
      query: { MoveEventType: eventType },
      cursor,
      limit,
      order: "ascending",
    });

    const events = result.data.map((event) => {
      const fields = event.parsedJson as Record<string, unknown>;
      // ephemeral_pubkey and metadata are vector<u8> → parsedJson gives number[]
      const ephPubRaw = fields.ephemeral_pubkey as number[];
      const metadataRaw = fields.metadata as number[] | null;
      return {
        ephemeralPubkey: bytesToHex(Uint8Array.from(ephPubRaw)),
        viewTag: Number(fields.view_tag),
        stealthAddress: fields.stealth_address as string,
        metadata: metadataRaw && metadataRaw.length > 0
          ? bytesToHex(Uint8Array.from(metadataRaw))
          : null,
        txDigest: event.id.txDigest,
        timestamp: String(event.timestampMs),
      };
    });

    return {
      events,
      nextCursor: result.nextCursor ?? null,
      hasNextPage: result.hasNextPage,
    };
  }

  getAdminAddress(): string {
    return this.adminKeypair.getPublicKey().toSuiAddress();
  }
}
