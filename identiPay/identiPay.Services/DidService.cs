using identiPay.Core.Entities;
using identiPay.Core.Interfaces;
using identiPay.Data;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;

namespace identiPay.Services;

public class DidService(IdentiPayDbContext dbContext, ILogger<DidService> logger) : IDidService {
    private readonly IdentiPayDbContext _dbContext = dbContext;
    private readonly ILogger<DidService> _logger = logger;

    public async Task<IdentiPayDid> RegisterDidAsync(Guid userId, string publicKey, string hostname, CancellationToken cancellationToken = default) {
        if (userId == Guid.Empty)
            throw new ArgumentException("User ID cannot be null", nameof(userId));
        if (string.IsNullOrWhiteSpace(hostname))
            throw new ArgumentException("Hostname cannot be null or whitespace.", nameof(hostname));
        if (string.IsNullOrWhiteSpace(publicKey))
            throw new ArgumentException("Public key cannot be null or whitespace.", nameof(publicKey));

        var newDid = IdentiPayDid.CreateNew(userId, hostname, publicKey);

        var existingDid = _dbContext.Dids
            .AsNoTracking()
            .FirstOrDefault(d => d.Hostname == hostname && d.UserId == userId);

        if (existingDid != null) {
            _logger.LogWarning("DID already exists for user {UserId} with hostname {Hostname}", userId, hostname);
            throw new InvalidOperationException($"A DID with hostname '{hostname}' already exists for user '{userId}'.");
        }

        var foundUser = await _dbContext.Users
            .FirstOrDefaultAsync(u => u.Id == userId, cancellationToken);

        if (foundUser == null) {
            _logger.LogWarning("User not found for user ID {UserId}", userId);
            throw new InvalidOperationException($"User with ID '{userId}' not found.");
        }

        newDid.SetUser(foundUser);
        await _dbContext.Dids.AddAsync(newDid, cancellationToken);

        foundUser.SetDid(newDid);
        _dbContext.Users.Update(foundUser);

        await _dbContext.SaveChangesAsync(cancellationToken);

        _logger.LogInformation("New DID registered: {DidString} ", newDid.ToDidString());
        return newDid;
    }

    public Task<IdentiPayDid> UpdateDidAsync(string did, string newPublicKey, CancellationToken cancellationToken = default) {
        throw new NotImplementedException();
    }
}