using identiPay.Core.Entities;
using identiPay.Core.Interfaces;
using identiPay.Data;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;

namespace identiPay.Services;

public class DidService(IdentiPayDbContext dbContext, ILogger<DidService> logger) : IDidService {
    private readonly IdentiPayDbContext _dbContext = dbContext ?? throw new ArgumentNullException(nameof(dbContext));
    private readonly ILogger<DidService> _logger = logger ?? throw new ArgumentNullException(nameof(logger));

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

    public async Task<IdentiPayDid> UpdateDidAsync(Guid didId, string newPublicKey, CancellationToken cancellationToken = default) {
        if (didId == Guid.Empty)
            throw new ArgumentException("DID ID cannot be null", nameof(didId));
        if (string.IsNullOrWhiteSpace(newPublicKey))
            throw new ArgumentException("New public key cannot be null or whitespace.", nameof(newPublicKey));

        var existingDid = _dbContext.Dids
            .FirstOrDefault(d => d.Id == didId);

        if (existingDid == null) {
            _logger.LogWarning("DID not found for DID ID {DidId}", didId);
            throw new InvalidOperationException($"DID with ID '{didId}' not found.");
        }

        existingDid.SetPublicKey(newPublicKey);
        _dbContext.Dids.Update(existingDid);

        await _dbContext.SaveChangesAsync(cancellationToken);

        _logger.LogInformation("DID updated: {DidString} ", existingDid.ToDidString());
        return existingDid;
    }
}