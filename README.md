<img width="100%" src="identiPay_logo.png" alt="identiPay Logo"/>

---

## Overview

<table style="margin: 0 auto;">
  <tr>
    <td style="text-align: center; vertical-align: middle;">
      <a href="https://creativecommons.org/licenses/by-nc-nd/4.0/">
        <img alt="License: CC BY-NC-ND" src="https://upload.wikimedia.org/wikipedia/commons/7/70/CC_BY-NC-ND.svg" width="120"/>
      </a>
    </td>
    <td style="text-align: center; vertical-align: middle;">
      <strong>identiPay</strong> © 2025 by Krum Sultov<br/>
      <small>Licensed under CC BY-NC-ND 4.0</small>
    </td>
  </tr>
</table>

IdentiPay is a next-generation payment network prototype designed to address the fundamental security, privacy, and user
control limitations of traditional card-based systems. By leveraging the principles of **Self-Sovereign Identity (SSI)**, IdentiPay empowers users with true ownership of their digital identity and financial credentials, enabling secure,
private, and seamless transactions directly via a secure and convenient mobile wallet.

Forget sharing vulnerable card numbers. With IdentiPay, you **pay with your identity**.

## The Problem

Today's digital payment infrastructure, while convenient, faces significant challenges:

* Card fraud due to reliance on static, easily compromised card numbers.
* **Lack of User Control.** Users have minimal control over how their sensitive financial and identity data is stored,
  shared, and used by numerous intermediaries.
* **Privacy Concerns:** Transactions often expose more personal information than necessary.
* **Limited Innovation:** Legacy systems hinder the integration of identity-aware value-added services.

## The IdentiPay Solution

IdentiPay tackles these issues head-on by building a payment network where verified identity is the core authorization
mechanism.

**Core Principles:**

1. **Self-Sovereign Identity (SSI):** Users generate and manage their own cryptographic keys securely on their devices
   using the Android Keystore. Private keys *never* leave the user's control.
2. **Decentralized Identifiers (DIDs):** Each user is represented by a unique, persistent `did:identiPay` identifier,
   controlled by their private key.
3. **Verifiable Credentials (VCs):** (Planned/Partially Implemented) Cryptographically signed, tamper-proof digital
   attestations will be used for specific claims like payment authorization, KYC status, age verification, or service
   entitlements, enabling selective disclosure.
4. **Secure Wallet:** A native Android mobile wallet application acts as the user's agent for managing DIDs, keys, VCs (
   future), creating signatures, and authorizing transactions.
5. **Direct Authorization:** Payments are authorized via cryptographic signatures generated on the user's device,
   verifying ownership of the DID and intent, rather than transmitting sensitive account numbers.

## How it Works (Core Flow)

1. **Onboarding (Wallet):** User installs the wallet, generates an ECDSA key pair securely via Android Keystore. The
   public key is sent to the IdentiPay backend.
2. **DID Registration (Backend):** Backend creates a unique user record, generates the `provider-specific-id`,
   constructs the full `did:identiPay:<hostname>:<provider-specific-id>`, associates it with the user and public key.
   The full DID is returned to the wallet.
3. **Transaction Offer (POS -> Backend):** A merchant POS initiates a payment request (Amount, Currency, Recipient DID,
   Type, Metadata) by calling the IdentiPay backend API (`POST /api/transactions/offer`). The backend creates a
   `TransactionPayload` and a `Transaction` record (status: `Pending`) linked via a unique `TransactionId`.
4. **Offer Presentation (POS -> Wallet):** The backend returns the `TransactionId`. The POS generates a QR code
   containing this `TransactionId`.
5. **Scan & Fetch (Wallet):** The user scans the QR code with their IdentiPay Wallet. The wallet uses the
   `TransactionId` to fetch the full transaction offer details from the backend API (`GET /api/transactions/{id}`).
6. **User Confirmation & Signing (Wallet):** The wallet displays the transaction details. Upon user approval, it prompts
   for biometric/device authentication to unlock the private key via Android Keystore. It then prepares a canonical
   representation of the `TransactionPayload` and signs its hash using the user's private key (ECDSA-SHA256).
7. **Completion Request (Wallet -> Backend):** The wallet sends the `TransactionId`, the user's `SenderDid`, and the
   generated `Signature` (Base64Url encoded) to the backend API (`POST /api/transactions/{id}/sign`).
8. **Verification & Completion (Backend):** The backend retrieves the transaction and payload. It fetches the
   `SenderDid`'s public key (via `IUserService`). It generates the same canonical payload representation and verifies
   the received signature against it using the public key. If valid, it updates the `Transaction` record (sets
   `SenderDid`, `Signature`, status: `Completed`) and saves changes.
9. **Status Update (Backend -> POS):** The POS app periodically polls the backend (`GET /api/transactions/{id}`) to
   check the transaction status, updating its UI when it sees `Completed` or `Failed`.

## Technology Stack

* **Backend:**
    * Language: **C#**
    * Framework: **.NET 9 / ASP.NET Core**
    * Architecture: **Modular Monolith** (Core, Data, Services, Api layers)
    * Data Access: **Entity Framework Core 9**
    * Database: **PostgreSQL**
    * Deployment: **Docker / Docker Compose**
    * Reverse Proxy: **Nginx**
* **IdentiPay Wallet (Android):**
    * Language: **Kotlin**
    * UI: **Jetpack Compose**
    * Architecture: **MVVM** (ViewModel, Repository patterns implied)
    * Networking: **Retrofit2 / OkHttp3**
    * Local Storage: **Room Persistence Library**
    * Cryptography: **Android Keystore (ECDSA P-256)**, standard Java/Android crypto APIs
    * QR Scanning: **ZXing Android Embedded**
* **IdentiPay POS (Android):**
    * Language: **Kotlin**
    * UI: **Jetpack Compose**
    * Networking: **Retrofit2 / OkHttp3**
    * QR Generation: **ZXing / QRGenerator wrapper**
* **Identity Primitives:**
    * DIDs: Custom `did:identiPay:<hostname>:<provider-specific-id>` method.
    * Signatures: ECDSA with SHA256.

## Future Work & Roadmap

* **Full Verifiable Credential (VC) Integration:**
    * Issue JWT-VCs from backend for key states (e.g., `PaymentAuthorization`, `IsOver18`, `IsOver21`).
    * Implement OID4VP/OID4VCI protocols for VC exchange between wallet and backend/relying parties.
    * Wallet UI for managing and selecting VCs.
    * Backend verification of VCs (issuer trust, revocation).
* **Advanced Transaction Types:** Implement specific backend logic and potential VC issuance for `Subscription` and
  `VAS` transaction types.
* **Enhanced Security:** Implement key rotation mechanisms, VC revocation lists (e.g., Status List 2021).
* **Interoperability & Federation:**
    * Define trust framework for `did:identiPay` providers (e.g., shared registry).
    * Explore optional support for standard DIDs (`did:web`, `did:key`).
    * Integrate EBSI VC verification.
* **User/Merchant Features:** Improve UI/UX, add transaction history details, merchant settings, etc.
* **Decentralized Registry PoC:** Implement the simplified 2-node PoC for DID resolution as explored.
