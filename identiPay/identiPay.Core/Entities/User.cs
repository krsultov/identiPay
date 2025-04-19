namespace identiPay.Core.Entities;

public class User {
    public Guid Id { get; private set; }

    public IdentiPayDid? Did { get; private set; }
    public Guid? DidId { get; private set; }
    public UserStatus Status { get; private set; }

    public string? MetadataJson { get; private set; }

    public DateTimeOffset CreatedAt { get; private set; }

    public DateTimeOffset ModifiedAt { get; private set; }

    private User(Guid id, UserStatus status, string? metadataJson) {
        Id = id;
        Status = status;
        MetadataJson = metadataJson;
        CreatedAt = DateTimeOffset.UtcNow;
        ModifiedAt = CreatedAt;
    }

    public void SetDid(IdentiPayDid did) {
        Did = did ?? throw new ArgumentNullException(nameof(did), "DID cannot be null.");
        DidId = did.Id;
    }

    public void SetStatus(UserStatus status) {
        Status = status switch {
            UserStatus.Active when Did == null => throw new InvalidOperationException("Cannot set status to Active without a DID."),
            _ => status
        };
    }

    public static User CreateNew() {
        return new User(Guid.NewGuid(), UserStatus.Pending, null);
    }
}

public enum UserStatus {
    Active,
    Pending,
    Suspended
}