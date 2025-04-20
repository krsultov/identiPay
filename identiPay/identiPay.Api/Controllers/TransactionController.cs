using System.ComponentModel.DataAnnotations;
using identiPay.Core.Entities;
using identiPay.Core.Interfaces;
using Microsoft.AspNetCore.Mvc;

namespace identiPay.Api.Controllers;

[ApiController]
[Route("api/transactions")]
public class TransactionController(ITransactionService transactionService, ILogger<TransactionController> logger) : ControllerBase {
    private readonly ITransactionService _transactionService = transactionService ?? throw new ArgumentNullException(nameof(transactionService));
    private readonly ILogger<TransactionController> _logger = logger ?? throw new ArgumentNullException(nameof(logger));

    public record SignTransactionRequest(
        [Required] string Signature,
        [Required] string SenderDid
    );

    public record CreateTransactionOfferRequest(
        [Required] string RecipientDid,
        [Required] TransactionType TransactionType,
        [Range(0.00000001, (double)decimal.MaxValue)]
        decimal Amount,
        [Required] [Length(3, 3)] string Currency,
        string? MetadataJson
    );

    [HttpPost("offer")]
    [ProducesResponseType(typeof(Transaction), StatusCodes.Status201Created)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    public async Task<ActionResult<Transaction>> CreateTransactionOffer([FromBody] CreateTransactionOfferRequest request, CancellationToken cancellationToken) {
        try {
            var transactionOffer = await _transactionService.CreateTransactionOfferAsync(
                request.RecipientDid,
                request.TransactionType,
                request.Amount,
                request.Currency,
                request.MetadataJson,
                cancellationToken);

            return CreatedAtAction(nameof(GetTransactionById), new { transactionId = transactionOffer.Id }, transactionOffer);
        }
        catch (ArgumentException ex) {
            _logger.LogWarning("Bad request creating transaction offer: {ErrorMessage}", ex.Message);
            return BadRequest(new { message = ex.Message });
        }
        catch (Exception ex) {
            _logger.LogError(ex, "Error creating transaction offer for Recipient DID {RecipientDid}", request.RecipientDid);
            return StatusCode(StatusCodes.Status500InternalServerError, "An unexpected error occurred creating the transaction offer.");
        }
    }

    [HttpGet("{transactionId:guid}")]
    [ProducesResponseType(typeof(Transaction), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public async Task<ActionResult<Transaction>> GetTransactionById(Guid transactionId, CancellationToken cancellationToken) {
        var transaction = await _transactionService.GetTransactionByIdAsync(transactionId, cancellationToken);

        if (transaction == null) {
            return NotFound();
        }

        return Ok(transaction);
    }

    [HttpPost("{transactionId:guid}/sign")]
    [ProducesResponseType(typeof(Transaction), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    [ProducesResponseType(StatusCodes.Status500InternalServerError)]
    public async Task<ActionResult<Transaction>> SignAndComplete(Guid transactionId, [FromBody] SignTransactionRequest request, CancellationToken cancellationToken) {
        try {
            var completedTransaction = await _transactionService.SignAndCompleteTransactionAsync(transactionId, request.SenderDid, request.Signature, cancellationToken);

            return Ok(completedTransaction);
        }
        catch (KeyNotFoundException ex) {
            _logger.LogInformation(ex, "Sign request failed: Transaction {TransactionId} not found.", transactionId);
            return NotFound(new { message = ex.Message });
        }
        catch (InvalidOperationException ex) {
            _logger.LogWarning("Sign request failed for Transaction {TransactionId}: {ErrorMessage}", transactionId, ex.Message);
            return BadRequest(new { message = ex.Message });
        }
        catch (ArgumentException ex) {
            _logger.LogWarning("Sign request failed due to invalid argument for Transaction {TransactionId}: {ErrorMessage}", transactionId, ex.Message);
            return BadRequest(new { message = ex.Message });
        }
        catch (Exception ex) {
            _logger.LogError(ex, "Unexpected error signing transaction {TransactionId}", transactionId);
            return StatusCode(StatusCodes.Status500InternalServerError, "An unexpected error occurred while signing the transaction.");
        }
    }
}