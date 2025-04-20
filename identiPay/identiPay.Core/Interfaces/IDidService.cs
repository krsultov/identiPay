using identiPay.Core.Entities;

namespace identiPay.Core.Interfaces;

public interface IDidService {
    Task<IdentiPayDid> RegisterDidAsync(Guid userId, string publicKey, string hostname, CancellationToken cancellationToken = default);
    Task<IdentiPayDid> UpdateDidAsync(Guid didId, string newPublicKey, CancellationToken cancellationToken = default);
}