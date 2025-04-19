namespace identiPay.Core.Entities;

public class IdentiPayDid {
    public Guid Id { get; private set; }
    public Guid UserId { get; private set; }
    public string Hostname { get; private set; }
    public string PublicKey { get; private set; }
    public User User { get; private set; } = null!;
    public DateTimeOffset CreatedAt { get; private set; }
    public DateTimeOffset ModifiedAt { get; private set; }

    private IdentiPayDid(Guid id, Guid userId, string hostname, string publicKey) {
        Id = id;
        UserId = userId;
        Hostname = hostname;
        PublicKey = publicKey;
        CreatedAt = DateTimeOffset.UtcNow;
        ModifiedAt = CreatedAt;
    }

    public string ToDidString() {
        return $"did:identipay:{Hostname}:{UserId}";
    }

    public void SetUser(User user) {
        User = user ?? throw new ArgumentNullException(nameof(user), "User cannot be null.");
        UserId = user.Id;
    }

    public void SetPublicKey(string publicKey) {
        if (string.IsNullOrWhiteSpace(publicKey))
            throw new ArgumentException("Public key cannot be null or whitespace.", nameof(publicKey));
        PublicKey = publicKey;
    }

    public void SetHostname(string hostname) {
        if (string.IsNullOrWhiteSpace(hostname))
            throw new ArgumentException("Hostname cannot be null or whitespace.", nameof(hostname));
        Hostname = hostname;
    }

    public static IdentiPayDid CreateNew(Guid userId, string hostname, string publicKey) {
        if (userId == Guid.Empty)
            throw new ArgumentException("User ID cannot be null or whitespace.", nameof(userId));
        if (string.IsNullOrWhiteSpace(hostname))
            throw new ArgumentException("Hostname cannot be null or whitespace.", nameof(hostname));
        if (string.IsNullOrWhiteSpace(publicKey))
            throw new ArgumentException("Public key cannot be null or whitespace.", nameof(publicKey));

        return new IdentiPayDid(Guid.NewGuid(), userId, hostname, publicKey);
    }
}