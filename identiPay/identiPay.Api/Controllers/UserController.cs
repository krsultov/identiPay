using identiPay.Core.Entities;
using identiPay.Core.Interfaces;
using Microsoft.AspNetCore.Mvc;

namespace identiPay.Api.Controllers;

[ApiController]
[Route("api/users")]
public class UserController(IUserService userService, ILogger<UserController> logger) : ControllerBase {
    private readonly ILogger<UserController> _logger = logger ?? throw new ArgumentNullException(nameof(logger));
    private readonly IUserService _userService = userService ?? throw new ArgumentNullException(nameof(userService));

    public record CreateUserRequest(
        [System.ComponentModel.DataAnnotations.Required]
        string PrimaryKey
    );

    [HttpPost]
    [ProducesResponseType(typeof(Guid), StatusCodes.Status201Created)]
    [ProducesResponseType(StatusCodes.Status400BadRequest)]
    public async Task<ActionResult<Guid>> CreateUser([FromBody] CreateUserRequest request, CancellationToken cancellationToken) {
        try {
            var newUser = await _userService.CreateUserAsync(request.PrimaryKey, cancellationToken);
            return CreatedAtAction(nameof(GetUserById), new { userId = newUser.Id }, newUser.Id);
        }
        catch (ArgumentException ex) {
            _logger.LogWarning("Bad request creating user: {ErrorMessage}", ex.Message);
            return BadRequest(new { message = ex.Message });
        }
        catch (Exception ex) {
            _logger.LogError(ex, "Error creating user");
            return StatusCode(StatusCodes.Status500InternalServerError, "An unexpected error occurred creating the user.");
        }
    }

    [HttpGet("{userId:guid}")]
    [ProducesResponseType(typeof(User), StatusCodes.Status200OK)]
    [ProducesResponseType(StatusCodes.Status404NotFound)]
    public async Task<ActionResult<User>> GetUserById(Guid userId, CancellationToken cancellationToken) {
        var user = await _userService.GetUserByIdAsync(userId, cancellationToken);

        if (user == null) {
            return NotFound();
        }

        return Ok(user);
    }
}