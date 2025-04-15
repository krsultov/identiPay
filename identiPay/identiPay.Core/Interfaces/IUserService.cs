using identiPay.Core.Entities;

namespace identiPay.Core.Interfaces;

public interface IUserService {
    Task<User> CreateUserAsync(string primaryDid, string primaryPublicKey,
        CancellationToken cancellationToken = default);

    Task<User?>? GetUserByIdAsync(Guid userId, CancellationToken cancellationToken = default);

    Task<User?> GetUserByDidAsync(string primaryDid, CancellationToken cancellationToken = default);

    Task DeleteUserByIdAsync(Guid userId, CancellationToken cancellationToken = default);
}