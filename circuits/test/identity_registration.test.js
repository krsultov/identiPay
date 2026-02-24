const chai = require("chai");
const { expect } = chai;
const path = require("path");
const circom_tester = require("circom_tester");
const wasm_tester = circom_tester.wasm;

// We use the built-in Poseidon from circomlibjs to compute expected outputs
let buildPoseidon;
try {
  buildPoseidon = require("circomlibjs").buildPoseidon;
} catch {
  // If circomlibjs is not installed, we'll skip expected-value checks
  buildPoseidon = null;
}

describe("Identity Registration Circuit", function () {
  this.timeout(60000);

  let circuit;
  let poseidon;
  let F; // finite field

  before(async function () {
    // Compile the circuit using wasm_tester
    circuit = await wasm_tester(
      path.join(__dirname, "..", "identity_registration.circom")
    );

    // Build Poseidon hash function for expected value computation
    if (buildPoseidon) {
      poseidon = await buildPoseidon();
      F = poseidon.F;
    }
  });

  it("should compute a valid identity commitment from valid inputs", async function () {
    const input = {
      issuerCertHash: "12345678901234567890",
      docNumberHash: "98765432109876543210",
      dobHash: "11111111111111111111",
      userSalt: "55555555555555555555",
    };

    const witness = await circuit.calculateWitness(input, true);
    await circuit.checkConstraints(witness);

    // The output should be non-zero
    const identityCommitment = witness[1]; // first output signal
    expect(identityCommitment).to.not.equal(0n);
  });

  it("should produce the correct Poseidon hash", async function () {
    if (!poseidon) {
      this.skip("circomlibjs not available for expected value computation");
    }

    const input = {
      issuerCertHash: "100",
      docNumberHash: "200",
      dobHash: "300",
      userSalt: "400",
    };

    const witness = await circuit.calculateWitness(input, true);
    await circuit.checkConstraints(witness);

    // Compute expected Poseidon(100, 200, 300, 400) using circomlibjs
    const expectedHash = poseidon([
      BigInt(input.issuerCertHash),
      BigInt(input.docNumberHash),
      BigInt(input.dobHash),
      BigInt(input.userSalt),
    ]);

    const circuitOutput = witness[1];
    const expectedOutput = F.toObject(expectedHash);

    expect(circuitOutput.toString()).to.equal(expectedOutput.toString());
  });

  it("should produce different commitments for different salts", async function () {
    const baseInput = {
      issuerCertHash: "12345678901234567890",
      docNumberHash: "98765432109876543210",
      dobHash: "11111111111111111111",
    };

    const input1 = { ...baseInput, userSalt: "1" };
    const input2 = { ...baseInput, userSalt: "2" };

    const witness1 = await circuit.calculateWitness(input1, true);
    const witness2 = await circuit.calculateWitness(input2, true);

    await circuit.checkConstraints(witness1);
    await circuit.checkConstraints(witness2);

    const commitment1 = witness1[1];
    const commitment2 = witness2[1];

    expect(commitment1.toString()).to.not.equal(commitment2.toString());
  });

  it("should produce different commitments for different document hashes", async function () {
    const baseInput = {
      issuerCertHash: "12345678901234567890",
      dobHash: "11111111111111111111",
      userSalt: "55555555555555555555",
    };

    const input1 = { ...baseInput, docNumberHash: "100" };
    const input2 = { ...baseInput, docNumberHash: "200" };

    const witness1 = await circuit.calculateWitness(input1, true);
    const witness2 = await circuit.calculateWitness(input2, true);

    await circuit.checkConstraints(witness1);
    await circuit.checkConstraints(witness2);

    const commitment1 = witness1[1];
    const commitment2 = witness2[1];

    expect(commitment1.toString()).to.not.equal(commitment2.toString());
  });

  it("should produce deterministic output for the same inputs", async function () {
    const input = {
      issuerCertHash: "42",
      docNumberHash: "43",
      dobHash: "44",
      userSalt: "45",
    };

    const witness1 = await circuit.calculateWitness(input, true);
    const witness2 = await circuit.calculateWitness(input, true);

    expect(witness1[1].toString()).to.equal(witness2[1].toString());
  });

  it("should accept zero values as valid inputs", async function () {
    const input = {
      issuerCertHash: "0",
      docNumberHash: "0",
      dobHash: "0",
      userSalt: "0",
    };

    const witness = await circuit.calculateWitness(input, true);
    await circuit.checkConstraints(witness);

    // Even with all zeros, there should be a valid commitment
    const identityCommitment = witness[1];
    expect(identityCommitment).to.not.be.undefined;
  });

  it("should accept large field element values", async function () {
    // Use values close to the BN254 scalar field order
    const input = {
      issuerCertHash:
        "21888242871839275222246405745257275088548364400416034343698204186575808495616",
      docNumberHash: "1",
      dobHash: "2",
      userSalt: "3",
    };

    const witness = await circuit.calculateWitness(input, true);
    await circuit.checkConstraints(witness);
  });
});
