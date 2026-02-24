import QRCode from "qrcode";

/**
 * Build a DID-based URI for a commerce proposal.
 * Format: did:identipay:<host>:<txId>
 * Wallet resolves via: GET https://<host>/api/identipay/v1/intents/<txId>
 */
export function buildProposalUri(hostname: string, transactionId: string): string {
  return `did:identipay:${hostname}:${transactionId}`;
}

/**
 * Build a pay-request URI.
 * Format: identipay://pay/@<name>.idpay?amount=X&currency=Y&memo=Z
 */
export function buildPayRequestUri(
  recipientName: string,
  amount: string,
  currency: string,
  memo?: string,
): string {
  const base = `identipay://pay/@${recipientName}.idpay?amount=${encodeURIComponent(amount)}&currency=${encodeURIComponent(currency)}`;
  if (memo) {
    return `${base}&memo=${encodeURIComponent(memo)}`;
  }
  return base;
}

/**
 * Generate a QR code as a data URL (PNG).
 */
export async function generateQrDataUrl(content: string): Promise<string> {
  return await QRCode.toDataURL(content, {
    errorCorrectionLevel: "M",
    margin: 2,
    width: 300,
  });
}
