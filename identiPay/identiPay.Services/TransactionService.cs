using identiPay.Core.Entities;
using identiPay.Core.Interfaces;
using identiPay.Data;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.Logging;

namespace identiPay.Services;

public class TransactionService(IdentiPayDbContext dbContext, ILogger<TransactionService> logger, IUserService userService) : ITransactionService {
    private readonly IdentiPayDbContext _dbContext = dbContext;
    private readonly IUserService _userService = userService;
    private readonly ILogger<TransactionService> _logger = logger;

    public async Task<Transaction> CreateTransactionOfferAsync(string senderDid, string recipientDid, TransactionType transactionType, decimal amount, string currency,
        string? metadataJson,
        CancellationToken cancellationToken = default) {
        var senderUser = await _userService.GetUserByDidAsync(senderDid, cancellationToken);
        if (senderUser == null) {
            _logger.LogError("Attempted to create transaction offer for non-existent sender DID: {SenderDid}", senderDid);
            throw new KeyNotFoundException($"Sender User with DID '{senderDid}' not found.");
        }


        TransactionPayload newPayload;
        try {
            newPayload = TransactionPayload.CreateNew(transactionType, recipientDid, amount, metadataJson, currency);
        }
        catch (ArgumentException e) {
            _logger.LogWarning("Invalid parameters provided for payload creation: {ErrMsg}", e.Message);
            throw;
        }

        Transaction newTransaction;
        try {
            newTransaction = Transaction.CreateNew(senderDid, newPayload.Id);
            newTransaction.SetPayload(newPayload);
            newPayload.SetTransaction(newTransaction);
        }
        catch (ArgumentException e) {
            _logger.LogWarning("Invalid parameters provided for transaction creation: {ErrMsg}", e.Message);
            ;
            throw;
        }

        await _dbContext.TransactionPayloads.AddAsync(newPayload, cancellationToken);
        await _dbContext.Transactions.AddAsync(newTransaction, cancellationToken);

        await _dbContext.SaveChangesAsync(cancellationToken);

        _logger.LogInformation("Transaction offer created successfully: {TransactionId}", newTransaction.Id);
        return newTransaction;
    }

    public async Task<Transaction?>
        GetTransactionByIdAsync(Guid transactionId, CancellationToken cancellationToken = default) {
        if (transactionId == Guid.Empty) return null;

        var query = _dbContext.Transactions
            .AsQueryable()
            .Include(t => t.Payload);

        return await query.FirstOrDefaultAsync(t => t.Id == transactionId, cancellationToken);
    }

    public async Task<IEnumerable<Transaction>> GetTransactionsBySenderDidAsync(string senderDid,
        CancellationToken cancellationToken = default) {
        if (string.IsNullOrWhiteSpace(senderDid)) return [];

        return await _dbContext.Transactions
            .Where(t => t.SenderDid == senderDid)
            .Include(t => t.Payload)
            .OrderByDescending(t => t.CreatedAt)
            .AsNoTracking()
            .ToListAsync(cancellationToken);
    }

    public Task<Transaction> SignAndCompleteTransactionAsync(Guid transactionId, string signature,
        CancellationToken cancellationToken = default) {
        throw new NotImplementedException();
    }

    public async Task<Transaction> FailTransactionAsync(Guid transactionId,
        CancellationToken cancellationToken = default) {
        var transaction = await _dbContext.Transactions.FirstOrDefaultAsync(t => t.Id == transactionId, cancellationToken);

        if (transaction == null) {
            _logger.LogWarning("Attempted to fail non-existent Transaction ID: {TransactionId}", transactionId);
            throw new KeyNotFoundException($"Transaction with ID '{transactionId}' not found.");
        }

        try {
            transaction.UpdateStatus(TransactionStatus.Failed);

            await _dbContext.SaveChangesAsync(cancellationToken);
            _logger.LogInformation("Marked transaction {TransactionId} as Failed.", transaction.Id);

            return transaction;
        }
        catch (InvalidOperationException ex) {
            _logger.LogWarning("Failed to mark transaction {TransactionId} as failed: {ErrorMessage}", transactionId, ex.Message);
            throw;
        }
        catch (Exception ex) {
            _logger.LogError(ex, "Error failing transaction {TransactionId}", transactionId);
            throw;
        }
    }
}