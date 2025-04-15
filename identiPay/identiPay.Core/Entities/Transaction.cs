namespace identiPay.Core.Entities;

public class Transaction {
    public Guid Id { get; private set; }
    public string SenderDid { get; private set; }
    public TransactionPayload Payload { get; private set; } = null!;
    public Guid PayloadId { get; private set; }
    public TransactionStatus Status { get; private set; }
    public string Signature { get; private set; }
    public DateTimeOffset CreatedAt { get; private set; }
    public DateTimeOffset ModifiedAt { get; private set; }

    private Transaction(Guid id, string senderDid) {
        Id = id;
        SenderDid = senderDid;
        Status = TransactionStatus.Pending;
        Signature = string.Empty;
        CreatedAt = DateTimeOffset.UtcNow;
        ModifiedAt = CreatedAt;
    }

    public static Transaction CreateNew(string senderDid) {
        if (string.IsNullOrWhiteSpace(senderDid))
            throw new ArgumentException("Sender DID cannot be null or whitespace.", nameof(senderDid));

        return new Transaction(Guid.NewGuid(), senderDid);
    }

    public void SetPayload(TransactionPayload payload) {
        if (Payload != null)
            throw new InvalidOperationException("Transaction payload is already set.");

        Payload = payload ?? throw new ArgumentNullException(nameof(payload), "Payload cannot be null.");
        PayloadId = payload.Id;
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

        switch (status) {
            case TransactionStatus.Pending:
                throw new InvalidOperationException(
                    "Cannot set transaction status to Pending after it has been modified.");
            case TransactionStatus.Completed when Signature == string.Empty:
                throw new InvalidOperationException(
                    "Cannot set transaction status to Completed without a valid signature.");
            case TransactionStatus.Completed when Status != TransactionStatus.Pending:
                throw new InvalidOperationException(
                    "Cannot set transaction status to Completed unless it is currently Pending.");
            case TransactionStatus.Failed when Status != TransactionStatus.Pending:
                throw new InvalidOperationException(
                    "Cannot set transaction status to Failed unless it is currently Pending.");
            default:
                Status = status;
                break;
        }
    }
}

public enum TransactionStatus {
    Pending,
    Completed,
    Failed
}