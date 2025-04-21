FROM mcr.microsoft.com/dotnet/sdk:9.0 AS build-env
WORKDIR /app

COPY *.sln .
COPY identiPay.Core/*.csproj ./identiPay.Core/
COPY identiPay.Data/*.csproj ./identiPay.Data/
COPY identiPay.Services/*.csproj ./identiPay.Services/
COPY identiPay.Api/*.csproj ./identiPay.Api/
RUN dotnet restore

COPY . .

WORKDIR /app/identiPay.Api
RUN dotnet publish -c Release -o /app/publish --no-restore

FROM mcr.microsoft.com/dotnet/aspnet:9.0
WORKDIR /app

ENV ASPNETCORE_ENVIRONMENT=Production
ENV ASPNETCORE_URLS=http://+:8080

COPY --from=build-env /app/publish .

EXPOSE 8080

ENTRYPOINT ["dotnet", "identiPayApi.dll"]
