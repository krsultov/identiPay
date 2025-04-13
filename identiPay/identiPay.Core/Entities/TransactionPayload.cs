namespace identiPay.Core.Entities;

public class TransactionPayload {
    public Guid Id { get; private set; }
    public string RecipientDid { get; private set; }
    public int Amount { get; private set; }
    public string? MetadataJson { get; private set; }
    public DateTimeOffset CreatedAt { get; private set; }
    public DateTimeOffset ModifiedAt { get; private set; }

    public Transaction Transaction { get; private set; } = null!;

    private TransactionPayload(Guid id, string recipientDid, int amount, string? metadataJson) {
        Id = id;
        RecipientDid = recipientDid;
        Amount = amount;
        MetadataJson = metadataJson;
        CreatedAt = DateTimeOffset.UtcNow;
        ModifiedAt = CreatedAt;
    }

    public static TransactionPayload CreateNew(string recipientDid, int amount, string? metadataJson) {
        if (string.IsNullOrWhiteSpace(recipientDid))
            throw new ArgumentException("Recipient DID cannot be null or whitespace.", nameof(recipientDid));
        if (amount <= 0)
            throw new ArgumentException("Amount must be greater than zero.", nameof(amount));

        return new TransactionPayload(Guid.NewGuid(), recipientDid, amount, metadataJson);
    }
}