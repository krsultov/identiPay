using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace identiPay.Data.Migrations
{
    /// <inheritdoc />
    public partial class DidIdNotRequired : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "fk_users_di_ds_did_id",
                table: "Users");

            migrationBuilder.AlterColumn<Guid>(
                name: "did_id",
                table: "Users",
                type: "uuid",
                nullable: true,
                oldClrType: typeof(Guid),
                oldType: "uuid");

            migrationBuilder.AddForeignKey(
                name: "fk_users_di_ds_did_id",
                table: "Users",
                column: "did_id",
                principalTable: "DIDs",
                principalColumn: "id");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropForeignKey(
                name: "fk_users_di_ds_did_id",
                table: "Users");

            migrationBuilder.AlterColumn<Guid>(
                name: "did_id",
                table: "Users",
                type: "uuid",
                nullable: false,
                defaultValue: new Guid("00000000-0000-0000-0000-000000000000"),
                oldClrType: typeof(Guid),
                oldType: "uuid",
                oldNullable: true);

            migrationBuilder.AddForeignKey(
                name: "fk_users_di_ds_did_id",
                table: "Users",
                column: "did_id",
                principalTable: "DIDs",
                principalColumn: "id",
                onDelete: ReferentialAction.Cascade);
        }
    }
}
