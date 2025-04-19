using identiPay.Core.Entities;

namespace identiPay.Core.Interfaces;

public interface IUserService {
    Task<User> CreateUserAsync(string primaryKey, CancellationToken cancellationToken = default);

    Task<User?> GetUserByIdAsync(Guid userId, CancellationToken cancellationToken = default);

    Task<User?> GetUserByDidAsync(IdentiPayDid userDid, CancellationToken cancellationToken = default);

    Task DeleteUserByIdAsync(Guid userId, CancellationToken cancellationToken = default);
}