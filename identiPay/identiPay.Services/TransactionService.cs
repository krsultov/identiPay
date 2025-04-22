using System.Globalization;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using identiPay.Core.Entities;
using identiPay.Core.Interfaces;
using identiPay.Data;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;

namespace identiPay.Services;

public class TransactionService(IdentiPayDbContext dbContext, ILogger<TransactionService> logger, IUserService userService) : ITransactionService {
    private readonly IdentiPayDbContext _dbContext = dbContext;
    private readonly IUserService _userService = userService;
    private readonly ILogger<TransactionService> _logger = logger;

    public async Task<Transaction> CreateTransactionOfferAsync(string recipientDid, TransactionType transactionType, decimal amount, string currency,
        string? metadataJson, CancellationToken cancellationToken = default) {
        TransactionPayload newPayload;
        try {
            newPayload = TransactionPayload.CreateNew(transactionType, recipientDid, amount, metadataJson, currency);
        }
        catch (ArgumentException e) {
            _logger.LogWarning("Invalid parameters provided for payload creation: {ErrMsg}", e.Message);
            throw;
        }

        Transaction newTransaction;
        try {
            newTransaction = Transaction.CreateNew(newPayload.Id);
            newTransaction.SetPayload(newPayload);
            newPayload.SetTransaction(newTransaction);
        }
        catch (ArgumentException e) {
            _logger.LogWarning("Invalid parameters provided for transaction creation: {ErrMsg}", e.Message);
            throw;
        }

        await _dbContext.TransactionPayloads.AddAsync(newPayload, cancellationToken);
        await _dbContext.Transactions.AddAsync(newTransaction, cancellationToken);

        await _dbContext.SaveChangesAsync(cancellationToken);

        _logger.LogInformation("Transaction offer created successfully: {TransactionId}", newTransaction.Id);
        return newTransaction;
    }

    public async Task<Transaction?> GetTransactionByIdAsync(Guid transactionId, CancellationToken cancellationToken = default) {
        if (transactionId == Guid.Empty) return null;

        var query = _dbContext.Transactions
            .AsQueryable()
            .Include(t => t.Payload);

        return await query.FirstOrDefaultAsync(t => t.Id == transactionId, cancellationToken);
    }

    public async Task<IEnumerable<Transaction>> GetTransactionsBySenderDidAsync(string senderDid, CancellationToken cancellationToken = default) {
        if (string.IsNullOrWhiteSpace(senderDid)) return [];

        return await _dbContext.Transactions
            .Where(t => t.SenderDid == senderDid)
            .Include(t => t.Payload)
            .OrderByDescending(t => t.CreatedAt)
            .AsNoTracking()
            .ToListAsync(cancellationToken);
    }

    public async Task<Transaction> SignAndCompleteTransactionAsync(Guid transactionId, string senderDid, string signature, CancellationToken cancellationToken = default) {
        if (transactionId == Guid.Empty) throw new ArgumentException("Invalid Transaction ID.", nameof(transactionId));
        if (string.IsNullOrWhiteSpace(signature)) throw new ArgumentException("Signature cannot be empty.", nameof(signature));
        if (string.IsNullOrWhiteSpace(senderDid)) throw new ArgumentException("Sender DID cannot be empty.", nameof(senderDid));

        var transaction = await _dbContext.Transactions
            .Include(t => t.Payload)
            .FirstOrDefaultAsync(t => t.Id == transactionId, cancellationToken);

        if (transaction == null) {
            _logger.LogWarning("Attempted to sign non-existent Transaction ID: {TransactionId}", transactionId);
            throw new KeyNotFoundException($"Transaction with ID '{transactionId}' not found.");
        }

        if (transaction.Status != TransactionStatus.Pending) {
            _logger.LogWarning("Attempted to sign transaction {TransactionId} which is not Pending (Status: {Status})", transactionId, transaction.Status);
            throw new InvalidOperationException($"Transaction is not in Pending state (current: {transaction.Status}).");
        }

        transaction.SetSenderDid(senderDid);

        if (string.IsNullOrWhiteSpace(transaction.SenderDid)) {
            _logger.LogError("Cannot verify signature for transaction {TransactionId} because SenderDid is missing.", transactionId);
            throw new InvalidOperationException("Transaction Sender DID is missing. Cannot verify signature.");
        }

        var userId = transaction.SenderDid.Split(":")[3];
        var senderGuid = Guid.Parse(userId);

        try {
            var senderUser = await _userService.GetUserByIdAsync(senderGuid, cancellationToken);
            if (senderUser?.Did == null) {
                _logger.LogError("Sender User or Public Key not found for DID: {SenderDid} on transaction {TransactionId}", transaction.SenderDid, transactionId);
                throw new InvalidOperationException($"Sender user or public key not found for transaction {transactionId}. Cannot verify signature.");
            }

            var publicKeyString = senderUser.Did.PublicKey;

            byte[] dataToVerifyBytes;
            try {
                dataToVerifyBytes = PreparePayloadForSigning(transaction.Payload);
            }
            catch (Exception ex) {
                _logger.LogError(ex, "Failed to prepare payload data for signature verification for transaction {TransactionId}", transactionId);
                throw new InvalidOperationException("Failed to prepare payload data for verification.", ex);
            }

            byte[] signatureBytes;
            try {
                signatureBytes = Base64UrlDecode(signature);
            }
            catch (FormatException ex) {
                _logger.LogWarning("Invalid Base64Url format for signature on transaction {TransactionId}", transactionId);
                throw new ArgumentException("Invalid signature format.", nameof(signature), ex);
            }


            bool isSignatureValid;
            try {
                isSignatureValid = VerifySignatureEcdsaSha256(publicKeyString, dataToVerifyBytes, signatureBytes);
            }
            catch (Exception ex) {
                _logger.LogError(ex, "Cryptographic verification failed unexpectedly for transaction {TransactionId}", transactionId);
                isSignatureValid = false;
            }


            if (!isSignatureValid) {
                _logger.LogWarning("Invalid signature provided for transaction {TransactionId} by sender {SenderDid}", transactionId, transaction.SenderDid);
                throw new InvalidOperationException("Invalid transaction signature.");
            }

            _logger.LogInformation("Signature verified successfully for transaction {TransactionId}", transactionId);

            transaction.SetSignature(signature);
            transaction.UpdateStatus(TransactionStatus.Completed);

            await _dbContext.SaveChangesAsync(cancellationToken);
            _logger.LogInformation("Signed and completed transaction {TransactionId}", transaction.Id);

            return transaction;
        }
        catch (InvalidOperationException ex) {
            _logger.LogWarning("Failed to sign/complete transaction {TransactionId}: {ErrorMessage}", transactionId, ex.Message);
            await FailTransactionAsync(transactionId, cancellationToken);
            throw;
        }
        catch (KeyNotFoundException ex) {
            _logger.LogError(ex, "Dependency not found during signing process for transaction {TransactionId}", transactionId);
            await FailTransactionAsync(transactionId, cancellationToken);
            throw;
        }
        catch (Exception ex) {
            _logger.LogError(ex, "Unexpected error signing and completing transaction {TransactionId}", transactionId);
            await FailTransactionAsync(transactionId, cancellationToken);
            throw;
        }
    }

    public async Task<Transaction> FailTransactionAsync(Guid transactionId, CancellationToken cancellationToken = default) {
        var transaction = await _dbContext.Transactions.FirstOrDefaultAsync(t => t.Id == transactionId, cancellationToken);

        if (transaction == null) {
            _logger.LogWarning("Attempted to fail non-existent Transaction ID: {TransactionId}", transactionId);
            throw new KeyNotFoundException($"Transaction with ID '{transactionId}' not found.");
        }

        try {
            transaction.UpdateStatus(TransactionStatus.Failed);

            await _dbContext.SaveChangesAsync(cancellationToken);
            _logger.LogInformation("Marked transaction {TransactionId} as Failed.", transaction.Id);

            return transaction;
        }
        catch (InvalidOperationException ex) {
            _logger.LogWarning("Failed to mark transaction {TransactionId} as failed: {ErrorMessage}", transactionId, ex.Message);
            throw;
        }
        catch (Exception ex) {
            _logger.LogError(ex, "Error failing transaction {TransactionId}", transactionId);
            throw;
        }
    }

    private byte[] PreparePayloadForSigning(TransactionPayload payload) {
        var canonicalPayload = new {
            Id = payload.Id.ToString(),
            Type = payload.Type.ToString(),
            payload.RecipientDid,
            Amount = payload.Amount.ToString("F8", CultureInfo.InvariantCulture),
            payload.Currency,
            //payload.MetadataJson
        };

        var jsonPayload = JsonSerializer.Serialize(canonicalPayload, new JsonSerializerOptions {
            PropertyNamingPolicy = null,
            WriteIndented = false,
            DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull
        });

        _logger.LogInformation("Prepared payload for signing: {JsonPayload}", jsonPayload);

        return Encoding.UTF8.GetBytes(jsonPayload);
    }

    private bool VerifySignatureEcdsaSha256(string base64DerPublicKey, byte[] dataToVerify, byte[] signatureBytes) {
        _logger.LogDebug("Attempting ECDSA SHA256 verification (Simplified). Public Key String (start): {PubKeyStart}, Data Length: {DataLength}, Signature Length: {SigLength}",
            base64DerPublicKey[..Math.Min(base64DerPublicKey.Length, 10)], dataToVerify.Length, signatureBytes.Length);

        ECDsa? ecdsa = null;
        try {
            // Public Key, assuming Base64 encoded DER SPKI format
            byte[] publicKeyBytes;
            try {
                // assuming the key is stored in base64 format in DB
                publicKeyBytes = Convert.FromBase64String(base64DerPublicKey);
            }
            catch (FormatException ex) {
                _logger.LogWarning(ex, "Stored public key is not valid Base64: {PubKeyStart}...", base64DerPublicKey[..Math.Min(base64DerPublicKey.Length, 10)]);
                return false;
            }

            ecdsa = ECDsa.Create();
            ecdsa.ImportSubjectPublicKeyInfo(publicKeyBytes, out _);
            _logger.LogDebug("Successfully imported public key assuming Base64 DER/SPKI format.");

            // Assume SHA256 hashing and ASN.1/DER signature format (RFC3279)
            var isValid = ecdsa.VerifyData(dataToVerify, signatureBytes, HashAlgorithmName.SHA256, DSASignatureFormat.Rfc3279DerSequence);

            _logger.LogDebug("ECDSA verification result: {IsValid}", isValid);
            return isValid;
        }
        catch (CryptographicException ex) {
            _logger.LogWarning(ex, "Cryptographic error during verification (likely invalid key/signature format) for key: {PubKeyStart}...",
                base64DerPublicKey.Substring(0, Math.Min(base64DerPublicKey.Length, 10)));
            return false;
        }
        catch (Exception ex) {
            _logger.LogError(ex, "Unexpected error during simplified signature verification process.");
            return false;
        }
        finally {
            ecdsa?.Dispose();
        }
    }

    private static byte[] Base64UrlDecode(string input) {
        var output = input;
        output = output.Replace('-', '+');
        output = output.Replace('_', '/');
        switch (output.Length % 4) {
            case 0: break;
            case 2: output += "=="; break;
            case 3: output += "="; break;
            default: throw new FormatException("Illegal base64url string!");
        }

        try {
            return Convert.FromBase64String(output);
        }
        catch (FormatException e) {
            throw new FormatException("Failed to decode base64url string after padding.", e);
        }
    }
}