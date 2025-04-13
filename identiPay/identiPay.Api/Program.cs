using identiPay.Core.Interfaces;
using identiPay.Data;
using identiPay.Services;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);


var connectionString = builder.Configuration.GetConnectionString("DefaultConnection");

if (string.IsNullOrEmpty(connectionString)) throw new InvalidOperationException("DefaultConnection string not found.");

builder.Services.AddDbContext<IdentiPayDbContext>(options =>
    options.UseNpgsql(connectionString,
            npgsqlOptionsAction: sqlOptions => {
                sqlOptions.MigrationsAssembly(typeof(IdentiPayDbContext).Assembly.FullName);
            })
        .UseSnakeCaseNamingConvention());


// Add services to the container.

builder.Services.AddControllers();

builder.Services.AddScoped<IUserService, UserService>();

// Learn more about configuring OpenAPI at https://aka.ms/aspnet/openapi
builder.Services.AddOpenApi();

var app = builder.Build();

// Configure the HTTP request pipeline.
if (app.Environment.IsDevelopment()) {
    app.MapOpenApi();
}

app.UseHttpsRedirection();

app.UseAuthorization();

app.MapControllers();

app.Run();