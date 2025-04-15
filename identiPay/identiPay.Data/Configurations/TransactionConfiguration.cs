using identiPay.Core.Entities;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace identiPay.Data.Configurations;

public class TransactionConfiguration : IEntityTypeConfiguration<Transaction> {
    public void Configure(EntityTypeBuilder<Transaction> builder) {
        builder.ToTable("Transactions");

        builder.HasKey(t => t.Id);

        builder.Property(t => t.Id)
            .HasColumnName("id")
            .ValueGeneratedOnAdd();

        builder.Property(t => t.SenderDid)
            .HasColumnName("sender_did")
            .IsRequired();

        builder.Property(t => t.Signature)
            .HasColumnName("signature");

        builder.Property(t => t.PayloadId)
            .HasColumnName("payload_id")
            .IsRequired();

        builder.Property(t => t.Status)
            .HasColumnName("status")
            .HasConversion<string>()
            .IsRequired();

        builder.Property(t => t.CreatedAt)
            .HasColumnName("created_at")
            .HasDefaultValueSql("now() at time zone 'utc'")
            .ValueGeneratedOnAdd()
            .IsRequired();

        builder.Property(t => t.ModifiedAt)
            .HasColumnName("modified_at")
            .ValueGeneratedOnAddOrUpdate()
            .HasDefaultValueSql("now() at time zone 'utc'")
            .IsRequired();

        builder.HasOne(t => t.Payload)
            .WithOne(p => p.Transaction)
            .HasForeignKey<Transaction>(t => t.PayloadId);
    }
}