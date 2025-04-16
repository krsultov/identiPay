using identiPay.Core.Entities;

namespace identiPay.Core.Interfaces;

public interface ITransactionService {
    Task<Transaction> CreateTransactionOfferAsync(string recipientDid, TransactionType transactionType, decimal amount,
        string currency, string? metadataJson, CancellationToken cancellationToken = default);

    Task<Transaction?> GetTransactionByIdAsync(Guid transactionId, CancellationToken cancellationToken = default);

    Task<IEnumerable<Transaction>> GetTransactionsBySenderDidAsync(string senderDid,
        CancellationToken cancellationToken = default);

    Task<Transaction> SignAndCompleteTransactionAsync(Guid transactionId, string signature,
        CancellationToken cancellationToken = default);

    Task<Transaction> FailTransactionAsync(Guid transactionId, CancellationToken cancellationToken = default);
}