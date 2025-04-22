using System.Text.Json.Serialization;

namespace identiPay.Core.Entities;

public class Transaction {
    public Guid Id { get; private set; }
    public string? SenderDid { get; private set; }
    public TransactionPayload Payload { get; private set; } = null!;
    public Guid PayloadId { get; private set; }
    public TransactionStatus Status { get; private set; }
    public string Signature { get; private set; }
    public DateTimeOffset CreatedAt { get; private set; }
    public DateTimeOffset ModifiedAt { get; private set; }

    private Transaction(Guid id, Guid payloadId) {
        Id = id;
        SenderDid = string.Empty;
        PayloadId = payloadId;
        Status = TransactionStatus.Pending;
        Signature = string.Empty;
        CreatedAt = DateTimeOffset.UtcNow;
        ModifiedAt = CreatedAt;
    }

    public static Transaction CreateNew(Guid payloadId) {
        if (payloadId == Guid.Empty) throw new ArgumentException("Payload Id cannot be null", nameof(payloadId));

        return new Transaction(Guid.NewGuid(), payloadId);
    }

    public void SetPayload(TransactionPayload payload) {
        if (Payload != null)
            throw new InvalidOperationException("Transaction payload is already set.");

        Payload = payload ?? throw new ArgumentNullException(nameof(payload), "Payload cannot be null.");
        PayloadId = payload.Id;
    }

    public void SetSenderDid(string senderDid) {
        if (Status != TransactionStatus.Pending) throw new InvalidOperationException("Transaction must be Pending to set a sender DID.");

        SenderDid = senderDid;
    }

    public void SetSignature(string signature) {
        if (string.IsNullOrWhiteSpace(signature))
            throw new ArgumentException("Signature cannot be null or whitespace.", nameof(signature));

        if (Status != TransactionStatus.Pending)
            throw new InvalidOperationException("Cannot set signature after transaction has been modified.");

        Signature = signature;
    }

    public void UpdateStatus(TransactionStatus status) {
        if (status == Status)
            throw new InvalidOperationException("Transaction status is already set to the specified value.");

        Status = status switch {
            TransactionStatus.Pending =>
                throw new InvalidOperationException("Cannot set transaction status to Pending after it has been modified."),
            TransactionStatus.Completed when string.IsNullOrWhiteSpace(SenderDid) =>
                throw new InvalidOperationException("Cannot set transaction status to Completed without a sender DID."),
            TransactionStatus.Completed when string.IsNullOrWhiteSpace(Signature) =>
                throw new InvalidOperationException("Cannot set transaction status to Completed without a valid signature."),
            TransactionStatus.Completed when Status != TransactionStatus.Pending =>
                throw new InvalidOperationException("Cannot set transaction status to Completed unless it is currently Pending."),
            TransactionStatus.Failed when Status != TransactionStatus.Pending =>
                throw new InvalidOperationException("Cannot set transaction status to Failed unless it is currently Pending."),
            _ => status
        };
    }
}

[JsonConverter(typeof(JsonStringEnumConverter))]
public enum TransactionStatus {
    Pending,
    Completed,
    Failed
}