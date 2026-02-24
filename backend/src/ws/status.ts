import { eq } from "drizzle-orm";
import { proposals } from "../db/schema.ts";
import type { Db } from "../db/connection.ts";

/** Minimal WebSocket interface for our connection tracking. */
interface WsConnection {
  send(data: string): void;
  close(): void;
}

// Map of transactionId -> set of WebSocket connections
const connections = new Map<string, Set<WsConnection>>();

/**
 * Handle a new WebSocket connection for transaction status updates.
 */
export function handleWsConnection(
  txId: string,
  ws: WsConnection,
  db: Db,
  onCleanup?: () => void,
) {
  if (!connections.has(txId)) {
    connections.set(txId, new Set());
  }
  connections.get(txId)!.add(ws);

  // Send current status on connect
  sendCurrentStatus(txId, ws, db);

  // Return cleanup function
  return () => {
    const set = connections.get(txId);
    if (set) {
      set.delete(ws);
      if (set.size === 0) {
        connections.delete(txId);
      }
    }
    onCleanup?.();
  };
}

/**
 * Send the current proposal status to a WebSocket client.
 */
async function sendCurrentStatus(txId: string, ws: WsConnection, db: Db) {
  try {
    const [proposal] = await db
      .select()
      .from(proposals)
      .where(eq(proposals.transactionId, txId))
      .limit(1);

    if (proposal) {
      ws.send(
        JSON.stringify({
          type: "status",
          transactionId: proposal.transactionId,
          status: proposal.status,
          suiTxDigest: proposal.suiTxDigest,
        }),
      );
    } else {
      ws.send(
        JSON.stringify({
          type: "error",
          message: "Transaction not found",
        }),
      );
    }
  } catch (error) {
    console.error("Failed to send status:", error);
  }
}

/**
 * Push a settlement update to all connected WebSocket clients for a transaction.
 * The buyer_stealth_address is NOT forwarded -- merchant only sees confirmation.
 */
export function pushSettlementUpdate(
  txId: string,
  status: string,
  suiTxDigest: string,
) {
  const set = connections.get(txId);
  if (!set) return;

  const message = JSON.stringify({
    type: "settlement",
    transactionId: txId,
    status,
    suiTxDigest,
  });

  for (const ws of set) {
    try {
      ws.send(message);
    } catch {
      set.delete(ws);
    }
  }
}

/**
 * Get the number of active connections (for monitoring).
 */
export function getActiveConnectionCount(): number {
  let count = 0;
  for (const set of connections.values()) {
    count += set.size;
  }
  return count;
}
