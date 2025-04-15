namespace identiPay.Core.Entities;

public class TransactionPayload {
    public Guid Id { get; private set; }
    public TransactionType Type { get; private set; }
    public string RecipientDid { get; private set; }
    public string Currency { get; private set; }
    public decimal Amount { get; private set; }
    public string? MetadataJson { get; private set; }
    public DateTimeOffset CreatedAt { get; private set; }
    public DateTimeOffset ModifiedAt { get; private set; }
    public Transaction Transaction { get; private set; } = null!;

    private TransactionPayload(Guid id, TransactionType type, string recipientDid, decimal amount, string? metadataJson,
        string currency) {
        Id = id;
        Type = type;
        RecipientDid = recipientDid;
        Amount = amount;
        MetadataJson = metadataJson;
        Currency = currency;
        CreatedAt = DateTimeOffset.UtcNow;
        ModifiedAt = CreatedAt;
    }

    public static TransactionPayload CreateNew(TransactionType type, string recipientDid, decimal amount,
        string? metadataJson, string currency) {
        if (string.IsNullOrWhiteSpace(recipientDid))
            throw new ArgumentException("Recipient DID cannot be null or whitespace.", nameof(recipientDid));
        if (amount <= 0)
            throw new ArgumentException("Amount must be greater than zero.", nameof(amount));
        if (string.IsNullOrWhiteSpace(currency) || currency.Length != 3)
            throw new ArgumentException("Invalid currency code.", nameof(currency));

        return new TransactionPayload(Guid.NewGuid(), type, recipientDid, amount, metadataJson, currency);
    }
}

public enum TransactionType {
    Payment,
    Transfer,
    Subscription,
    // Refund,
    // Withdrawal,
    // Deposit
}