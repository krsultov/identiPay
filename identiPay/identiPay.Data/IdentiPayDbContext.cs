using System.Reflection;
using identiPay.Core.Entities;
using Microsoft.EntityFrameworkCore;

namespace identiPay.Data;

public class IdentiPayDbContext(DbContextOptions<IdentiPayDbContext> options) : DbContext(options) {
    public DbSet<User> Users { get; set; } = null!;
    public DbSet<Transaction> Transactions { get; set; } = null!;
    public DbSet<TransactionPayload> TransactionPayloads { get; set; } = null!;
    public DbSet<IdentiPayDid> Dids { get; set; } = null!;

    protected override void OnModelCreating(ModelBuilder modelBuilder) {
        base.OnModelCreating(modelBuilder);

        modelBuilder.ApplyConfigurationsFromAssembly(Assembly.GetExecutingAssembly());
    }
}