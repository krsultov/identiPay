namespace identiPay.Core.Entities;

public class User {
    public Guid Id { get; private set; }

    public string PrimaryDid { get; private set; }

    public string PrimaryPublicKey { get; private set; }

    public UserStatus Status { get; private set; }

    public string? MetadataJson { get; private set; }

    public DateTimeOffset CreatedAt { get; private set; }

    public DateTimeOffset ModifiedAt { get; private set; }

    private User(Guid id, string primaryDid, string primaryPublicKey, UserStatus status, string? metadataJson) {
        Id = id;
        PrimaryDid = primaryDid;
        PrimaryPublicKey = primaryPublicKey;
        Status = status;
        MetadataJson = metadataJson;
        CreatedAt = DateTimeOffset.UtcNow;
        ModifiedAt = CreatedAt;
    }

    public static User CreateNew(string primaryDid, string primaryPublicKey) {
        if (string.IsNullOrWhiteSpace(primaryDid))
            throw new ArgumentException("Primary DID cannot be null or whitespace.", nameof(primaryDid));
        if (string.IsNullOrWhiteSpace(primaryPublicKey))
            throw new ArgumentException("Primary Public Key cannot be null or whitespace.", nameof(primaryPublicKey));

        return new User(Guid.NewGuid(), primaryDid, primaryPublicKey, UserStatus.Pending, null);
    }
}

public enum UserStatus {
    Active,
    Pending,
    Suspended
}