using identiPay.Core.Entities;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Metadata.Builders;

namespace identiPay.Data.Configurations;

public class IdentiPayDidConfiguration : IEntityTypeConfiguration<IdentiPayDid> {
    public void Configure(EntityTypeBuilder<IdentiPayDid> builder) {
        builder.ToTable("DIDs");

        builder.HasKey(d => d.Id);

        builder.Property(d => d.Id)
            .HasColumnName("id")
            .ValueGeneratedOnAdd();

        builder.Property(d => d.PublicKey)
            .HasColumnName("public_key")
            .IsRequired();

        builder.Property(d => d.Hostname)
            .HasColumnName("hostname")
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
    }
}