using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace identiPay.Data.Migrations
{
    /// <inheritdoc />
    public partial class TransactionRefactoring1 : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "fk_transaction_payloads_transactions_transaction_id",
                table: "TransactionPayloads");

            migrationBuilder.DropForeignKey(
                name: "fk_transactions_transaction_payload_payload_id",
                table: "Transactions");

            migrationBuilder.DropIndex(
                name: "ix_transaction_payloads_transaction_id",
                table: "TransactionPayloads");

            migrationBuilder.DropColumn(
                name: "transaction_id",
                table: "TransactionPayloads");

            migrationBuilder.AlterColumn<decimal>(
                name: "amount",
                table: "TransactionPayloads",
                type: "numeric(18,8)",
                precision: 18,
                scale: 8,
                nullable: false,
                oldClrType: typeof(int),
                oldType: "integer");

            migrationBuilder.AddColumn<string>(
                name: "currency",
                table: "TransactionPayloads",
                type: "character varying(3)",
                maxLength: 3,
                nullable: false,
                defaultValue: "");

            migrationBuilder.AddColumn<int>(
                name: "type",
                table: "TransactionPayloads",
                type: "integer",
                nullable: false,
                defaultValue: 0);

            migrationBuilder.AddForeignKey(
                name: "fk_transactions_transaction_payloads_payload_id",
                table: "Transactions",
                column: "payload_id",
                principalTable: "TransactionPayloads",
                principalColumn: "id",
                onDelete: ReferentialAction.Cascade);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "fk_transactions_transaction_payloads_payload_id",
                table: "Transactions");

            migrationBuilder.DropColumn(
                name: "currency",
                table: "TransactionPayloads");

            migrationBuilder.DropColumn(
                name: "type",
                table: "TransactionPayloads");

            migrationBuilder.AlterColumn<int>(
                name: "amount",
                table: "TransactionPayloads",
                type: "integer",
                nullable: false,
                oldClrType: typeof(decimal),
                oldType: "numeric(18,8)",
                oldPrecision: 18,
                oldScale: 8);

            migrationBuilder.AddColumn<Guid>(
                name: "transaction_id",
                table: "TransactionPayloads",
                type: "uuid",
                nullable: false,
                defaultValue: new Guid("00000000-0000-0000-0000-000000000000"));

            migrationBuilder.CreateIndex(
                name: "ix_transaction_payloads_transaction_id",
                table: "TransactionPayloads",
                column: "transaction_id");

            migrationBuilder.AddForeignKey(
                name: "fk_transaction_payloads_transactions_transaction_id",
                table: "TransactionPayloads",
                column: "transaction_id",
                principalTable: "Transactions",
                principalColumn: "id",
                onDelete: ReferentialAction.Cascade);

            migrationBuilder.AddForeignKey(
                name: "fk_transactions_transaction_payload_payload_id",
                table: "Transactions",
                column: "payload_id",
                principalTable: "TransactionPayloads",
                principalColumn: "id",
                onDelete: ReferentialAction.Cascade);
        }
    }
}
