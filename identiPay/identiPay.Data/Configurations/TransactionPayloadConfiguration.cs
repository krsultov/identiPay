using identiPay.Core.Entities;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace identiPay.Data.Configurations;

public class TransactionPayloadConfiguration : IEntityTypeConfiguration<TransactionPayload> {
    public void Configure(EntityTypeBuilder<TransactionPayload> builder) {
        builder.ToTable("TransactionPayloads");

        builder.HasKey(tp => tp.Id);

        builder.Property(tp => tp.Id)
            .HasColumnName("id")
            .ValueGeneratedOnAdd();

        builder.Property(tp => tp.RecipientDid)
            .HasColumnName("recipient_did")
            .IsRequired();

        builder.Property(tp => tp.Amount)
            .HasColumnName("amount")
            .IsRequired();

        builder.Property(tp => tp.MetadataJson)
            .HasColumnName("metadata_json")
            .HasColumnType("jsonb");

        builder.Property(tp => tp.CreatedAt)
            .HasColumnName("created_at")
            .HasDefaultValueSql("now() at time zone 'utc'")
            .IsRequired();

        builder.Property(tp => tp.ModifiedAt)
            .HasColumnName("modified_at")
            .ValueGeneratedOnAddOrUpdate()
            .HasDefaultValueSql("now() at time zone 'utc'")
            .IsRequired();
    }
}