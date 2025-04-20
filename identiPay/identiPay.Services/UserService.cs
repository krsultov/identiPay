using identiPay.Core.Entities;
using identiPay.Core.Interfaces;
using identiPay.Data;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;

namespace identiPay.Services;

public class UserService(IdentiPayDbContext dbContext, ILogger<UserService> logger, IDidService didService, IConfiguration configuration) : IUserService {
    private readonly IdentiPayDbContext _dbContext = dbContext ?? throw new ArgumentNullException(nameof(dbContext));
    private readonly ILogger<UserService> _logger = logger ?? throw new ArgumentNullException(nameof(logger));
    private readonly IDidService _didService = didService ?? throw new ArgumentNullException(nameof(didService));
    private readonly IConfiguration _configuration = configuration ?? throw new ArgumentNullException(nameof(configuration));

    public async Task<User> CreateUserAsync(string primaryKey, CancellationToken cancellationToken = default) {
        var hostname = _configuration["didHostname"];
        if (string.IsNullOrWhiteSpace(hostname)) {
            throw new InvalidOperationException("Hostname for DID registration is not configured.");
        }

        var newUser = User.CreateNew();

        await _dbContext.Users.AddAsync(newUser, cancellationToken);
        await _dbContext.SaveChangesAsync(cancellationToken);

        _logger.LogInformation("New Pending User created: {UserId}", newUser.Id);

        await _didService.RegisterDidAsync(newUser.Id, primaryKey, hostname, cancellationToken);
        _logger.LogInformation("New DID registered for user: {UserId}", newUser.Id);
        return newUser;
    }

    public async Task<User?> GetUserByIdAsync(Guid userId, CancellationToken cancellationToken = default) {
        if (userId == Guid.Empty) return null;

        var user = await _dbContext.Users
            .Include(u => u.Did)
            .FirstOrDefaultAsync(u => u.Id == userId, cancellationToken);

        return user;
    }

    public async Task<User?> GetUserByDidAsync(IdentiPayDid userDid, CancellationToken cancellationToken = default) {
        var user = await _dbContext.Users
            .FirstOrDefaultAsync(u => u.Did == userDid, cancellationToken);

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