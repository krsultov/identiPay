using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Design;
using Microsoft.Extensions.Configuration;
using identiPay.Data;

namespace IdentiPay.Data;

public class DesignTimeDbContextFactory : IDesignTimeDbContextFactory<IdentiPayDbContext> {
    public IdentiPayDbContext CreateDbContext(string[] args) {
        var environment = Environment.GetEnvironmentVariable("ASPNETCORE_ENVIRONMENT") ?? "Development";


        var currentDirectory = Directory.GetCurrentDirectory();
        var solutionRootPath = FindSolutionRoot(currentDirectory) ?? currentDirectory;

        var apiProjectDirectory = Path.Combine(solutionRootPath, "IdentiPay.Api");

        if (!Directory.Exists(apiProjectDirectory)) {
            throw new DirectoryNotFoundException($"The directory '{apiProjectDirectory}' does not exist.");
        }

        Console.WriteLine($"Using configuration base path: {apiProjectDirectory}");

        var configuration = new ConfigurationBuilder()
            .SetBasePath(apiProjectDirectory)
            .AddJsonFile("appsettings.json", optional: false, reloadOnChange: true)
            .AddJsonFile($"appsettings.{environment}.json", optional: true)
            .AddEnvironmentVariables()
            .Build();

        var optionsBuilder = new DbContextOptionsBuilder<IdentiPayDbContext>();

        var connectionString = configuration.GetConnectionString("DefaultConnection");
        Console.WriteLine($"DesignTime Connection String: {connectionString}");

        if (string.IsNullOrEmpty(connectionString)) {
            throw new InvalidOperationException("DefaultConnection string not found.");
        }

        optionsBuilder.UseNpgsql(connectionString,
                npgsqlOptionsAction: sqlOptions => {
                    sqlOptions.MigrationsAssembly(typeof(IdentiPayDbContext).Assembly.FullName);
                })
            .UseSnakeCaseNamingConvention();

        Console.WriteLine("DesignTimeDbContextFactory: Successfully configured DbContextOptions.");
        return new IdentiPayDbContext(optionsBuilder.Options);
    }

    private static string? FindSolutionRoot(string currentPath) {
        var directory = new DirectoryInfo(currentPath);
        while (directory != null && directory.GetFiles("*.sln").Length == 0) {
            directory = directory.Parent;
        }

        return directory?.FullName;
    }
}