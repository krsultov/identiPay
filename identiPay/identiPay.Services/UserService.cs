using identiPay.Core.Entities;
using identiPay.Core.Interfaces;
using identiPay.Data;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;

namespace identiPay.Services;

public class UserService(IdentiPayDbContext dbContext, ILogger<UserService> logger) : IUserService {
    private readonly IdentiPayDbContext _dbContext = dbContext ?? throw new ArgumentNullException(nameof(dbContext));
    private readonly ILogger<UserService> _logger = logger ?? throw new ArgumentNullException(nameof(logger));

    public async Task<User> CreateUserAsync(string primaryDid, string primaryPublicKey,
        CancellationToken cancellationToken = default) {
        var existingUser = await _dbContext.Users
            .AsNoTracking()
            .FirstOrDefaultAsync(u => u.PrimaryDid == primaryDid, cancellationToken);

        if (existingUser != null) {
            _logger.LogWarning("User already exists: {PrimaryDid}", primaryDid);
            throw new InvalidOperationException($"A user with DID '{primaryDid}' already exists.");
        }

        var newUser = User.CreateNew(primaryDid, primaryPublicKey);

        await _dbContext.Users.AddAsync(newUser, cancellationToken);
        await _dbContext.SaveChangesAsync(cancellationToken);

        _logger.LogInformation("New User created: {UserId}", newUser.Id);
        return newUser;
    }

    public async Task<User?> GetUserByIdAsync(Guid userId, CancellationToken cancellationToken = default) {
        if (userId == Guid.Empty) return null;

        var user = await _dbContext.Users
            .FirstOrDefaultAsync(u => u.Id == userId, cancellationToken);

        return user;
    }

    public async Task<User?> GetUserByDidAsync(string primaryDid, CancellationToken cancellationToken = default) {
        if (string.IsNullOrWhiteSpace(primaryDid)) return null;

        var user = await _dbContext.Users
            .FirstOrDefaultAsync(u => u.PrimaryDid == primaryDid, cancellationToken);

        return user;
    }

    public async Task DeleteUserByIdAsync(Guid userId, CancellationToken cancellationToken = default) {
        if (userId == Guid.Empty) return;

        var user = await _dbContext.Users
            .FirstOrDefaultAsync(u => u.Id == userId, cancellationToken);

        if (user == null) return;

        _dbContext.Users.Remove(user);
        await _dbContext.SaveChangesAsync(cancellationToken);

        _logger.LogInformation("User with ID {UserId} deleted.", userId);
    }
}