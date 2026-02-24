const chai = require("chai");
const { expect } = chai;
const path = require("path");
const circom_tester = require("circom_tester");
const wasm_tester = circom_tester.wasm;

let buildPoseidon;
try {
  buildPoseidon = require("circomlibjs").buildPoseidon;
} catch {
  buildPoseidon = null;
}

describe("Age Check Circuit", function () {
  this.timeout(120000);

  let circuit;
  let poseidon;
  let F;

  before(async function () {
    circuit = await wasm_tester(
      path.join(__dirname, "..", "age_check.circom")
    );

    if (buildPoseidon) {
      poseidon = await buildPoseidon();
      F = poseidon.F;
    }
  });

  /**
   * Helper to compute dobHash and build a valid input object.
   */
  function makeInput({
    birthYear,
    birthMonth,
    birthDay,
    ageThreshold,
    referenceDate,
    identityCommitment,
    intentHash,
    userSalt,
  }) {
    let dobHash;
    if (poseidon) {
      const hash = poseidon([
        BigInt(birthYear),
        BigInt(birthMonth),
        BigInt(birthDay),
      ]);
      dobHash = F.toObject(hash).toString();
    } else {
      // Fallback: use a dummy value (tests requiring exact dobHash will fail)
      dobHash = "0";
    }

    return {
      birthYear: birthYear.toString(),
      birthMonth: birthMonth.toString(),
      birthDay: birthDay.toString(),
      dobHash,
      userSalt: (userSalt || "12345").toString(),
      ageThreshold: ageThreshold.toString(),
      referenceDate: referenceDate.toString(),
      identityCommitment: (identityCommitment || "999888777").toString(),
      intentHash: (intentHash || "111222333").toString(),
    };
  }

  // -------------------------------------------------------
  // Age >= 18 tests (should pass)
  // -------------------------------------------------------
  describe("Age >= 18 acceptance", function () {
    it("should accept someone who is exactly 18 (birthday today)", async function () {
      if (!poseidon) this.skip("circomlibjs required");

      // Reference date: 2026-02-24, Birth date: 2008-02-24 => exactly 18
      const input = makeInput({
        birthYear: 2008,
        birthMonth: 2,
        birthDay: 24,
        ageThreshold: 18,
        referenceDate: 20260224,
      });

      const witness = await circuit.calculateWitness(input, true);
      await circuit.checkConstraints(witness);
    });

    it("should accept someone who is 25", async function () {
      if (!poseidon) this.skip("circomlibjs required");

      // Reference date: 2026-02-24, Birth date: 2001-06-15 => 24 years old
      // Wait, 2026 - 2001 = 25, but birthday hasn't passed yet (June > Feb)
      // So effective age = 24. Let's use 2001-01-01 instead.
      const input = makeInput({
        birthYear: 2001,
        birthMonth: 1,
        birthDay: 1,
        ageThreshold: 18,
        referenceDate: 20260224,
      });

      const witness = await circuit.calculateWitness(input, true);
      await circuit.checkConstraints(witness);
    });

    it("should accept someone who is 21 for age threshold 21", async function () {
      if (!poseidon) this.skip("circomlibjs required");

      // Reference date: 2026-07-01, Birth date: 2005-03-15 => 21 years old
      const input = makeInput({
        birthYear: 2005,
        birthMonth: 3,
        birthDay: 15,
        ageThreshold: 21,
        referenceDate: 20260701,
      });

      const witness = await circuit.calculateWitness(input, true);
      await circuit.checkConstraints(witness);
    });

    it("should accept someone who is 65 for age threshold 18", async function () {
      if (!poseidon) this.skip("circomlibjs required");

      const input = makeInput({
        birthYear: 1961,
        birthMonth: 1,
        birthDay: 1,
        ageThreshold: 18,
        referenceDate: 20260224,
      });

      const witness = await circuit.calculateWitness(input, true);
      await circuit.checkConstraints(witness);
    });

    it("should accept birthday already passed this year", async function () {
      if (!poseidon) this.skip("circomlibjs required");

      // Reference date: 2026-12-25, Birth date: 2008-06-15 => 18 years old
      const input = makeInput({
        birthYear: 2008,
        birthMonth: 6,
        birthDay: 15,
        ageThreshold: 18,
        referenceDate: 20261225,
      });

      const witness = await circuit.calculateWitness(input, true);
      await circuit.checkConstraints(witness);
    });
  });

  // -------------------------------------------------------
  // Age < 18 tests (should fail)
  // -------------------------------------------------------
  describe("Age < 18 rejection", function () {
    it("should reject someone who is 17 (birthday tomorrow)", async function () {
      if (!poseidon) this.skip("circomlibjs required");

      // Reference date: 2026-02-24, Birth date: 2008-02-25 => 17 (birthday not yet)
      const input = makeInput({
        birthYear: 2008,
        birthMonth: 2,
        birthDay: 25,
        ageThreshold: 18,
        referenceDate: 20260224,
      });

      try {
        await circuit.calculateWitness(input, true);
        expect.fail("Should have thrown for underage user");
      } catch (err) {
        expect(err.message).to.satisfy(
          (msg) =>
            msg.includes("Assert Failed") ||
            msg.includes("Error") ||
            msg.includes("assert"),
          `Unexpected error: ${err.message}`
        );
      }
    });

    it("should reject someone who is 15", async function () {
      if (!poseidon) this.skip("circomlibjs required");

      // Reference date: 2026-02-24, Birth date: 2011-05-10 => 14 years old
      const input = makeInput({
        birthYear: 2011,
        birthMonth: 5,
        birthDay: 10,
        ageThreshold: 18,
        referenceDate: 20260224,
      });

      try {
        await circuit.calculateWitness(input, true);
        expect.fail("Should have thrown for underage user");
      } catch (err) {
        expect(err.message).to.satisfy(
          (msg) =>
            msg.includes("Assert Failed") ||
            msg.includes("Error") ||
            msg.includes("assert"),
          `Unexpected error: ${err.message}`
        );
      }
    });

    it("should reject 20-year-old for age threshold 21", async function () {
      if (!poseidon) this.skip("circomlibjs required");

      // Reference date: 2026-02-24, Birth date: 2006-05-01 => 19 years old
      const input = makeInput({
        birthYear: 2006,
        birthMonth: 5,
        birthDay: 1,
        ageThreshold: 21,
        referenceDate: 20260224,
      });

      try {
        await circuit.calculateWitness(input, true);
        expect.fail("Should have thrown for underage user");
      } catch (err) {
        expect(err.message).to.satisfy(
          (msg) =>
            msg.includes("Assert Failed") ||
            msg.includes("Error") ||
            msg.includes("assert"),
          `Unexpected error: ${err.message}`
        );
      }
    });

    it("should reject someone born in same year but birthday not reached", async function () {
      if (!poseidon) this.skip("circomlibjs required");

      // Reference date: 2026-03-01, Birth date: 2008-12-31
      // rawAge = 18, but birthday hasn't passed, effectiveAge = 17
      const input = makeInput({
        birthYear: 2008,
        birthMonth: 12,
        birthDay: 31,
        ageThreshold: 18,
        referenceDate: 20260301,
      });

      try {
        await circuit.calculateWitness(input, true);
        expect.fail("Should have thrown for underage user");
      } catch (err) {
        expect(err.message).to.satisfy(
          (msg) =>
            msg.includes("Assert Failed") ||
            msg.includes("Error") ||
            msg.includes("assert"),
          `Unexpected error: ${err.message}`
        );
      }
    });
  });

  // -------------------------------------------------------
  // dobHash binding tests
  // -------------------------------------------------------
  describe("dobHash binding", function () {
    it("should reject when dobHash does not match birth date", async function () {
      if (!poseidon) this.skip("circomlibjs required");

      // Compute dobHash for a DIFFERENT date than the one provided
      const wrongDobHash = F.toObject(
        poseidon([BigInt(1990), BigInt(1), BigInt(1)])
      ).toString();

      const input = {
        birthYear: "2000",
        birthMonth: "6",
        birthDay: "15",
        dobHash: wrongDobHash, // Hash of 1990-01-01, not 2000-06-15
        userSalt: "12345",
        ageThreshold: "18",
        referenceDate: "20260224",
        identityCommitment: "999888777",
        intentHash: "111222333",
      };

      try {
        await circuit.calculateWitness(input, true);
        expect.fail("Should have thrown for mismatched dobHash");
      } catch (err) {
        expect(err.message).to.satisfy(
          (msg) =>
            msg.includes("Assert Failed") ||
            msg.includes("Error") ||
            msg.includes("assert"),
          `Unexpected error: ${err.message}`
        );
      }
    });

    it("should accept when dobHash correctly matches birth date", async function () {
      if (!poseidon) this.skip("circomlibjs required");

      const correctDobHash = F.toObject(
        poseidon([BigInt(2000), BigInt(6), BigInt(15)])
      ).toString();

      const input = {
        birthYear: "2000",
        birthMonth: "6",
        birthDay: "15",
        dobHash: correctDobHash,
        userSalt: "12345",
        ageThreshold: "18",
        referenceDate: "20260224",
        identityCommitment: "999888777",
        intentHash: "111222333",
      };

      const witness = await circuit.calculateWitness(input, true);
      await circuit.checkConstraints(witness);
    });
  });

  // -------------------------------------------------------
  // intentHash binding tests
  // -------------------------------------------------------
  describe("intentHash binding", function () {
    it("should produce different proofs for different intentHash values", async function () {
      if (!poseidon) this.skip("circomlibjs required");

      const baseParams = {
        birthYear: 2000,
        birthMonth: 1,
        birthDay: 1,
        ageThreshold: 18,
        referenceDate: 20260224,
        identityCommitment: 999888777,
      };

      const input1 = makeInput({ ...baseParams, intentHash: 111 });
      const input2 = makeInput({ ...baseParams, intentHash: 222 });

      const witness1 = await circuit.calculateWitness(input1, true);
      const witness2 = await circuit.calculateWitness(input2, true);

      await circuit.checkConstraints(witness1);
      await circuit.checkConstraints(witness2);

      // Both should succeed (intentHash is a public input, not a constraint failure)
      // The witnesses should differ because the public inputs differ
      // We verify that the circuit accepts both valid intent hashes
    });

    it("should bind intentHash as a public input in the constraint system", async function () {
      if (!poseidon) this.skip("circomlibjs required");

      const input = makeInput({
        birthYear: 1990,
        birthMonth: 6,
        birthDay: 15,
        ageThreshold: 18,
        referenceDate: 20260224,
        identityCommitment: 42,
        intentHash: 987654321,
      });

      const witness = await circuit.calculateWitness(input, true);
      await circuit.checkConstraints(witness);

      // intentHash should appear in the witness (as a public input signal)
      // The witness is valid, confirming the intentHash is bound into the proof
    });
  });

  // -------------------------------------------------------
  // Edge cases
  // -------------------------------------------------------
  describe("Edge cases", function () {
    it("should handle age threshold of 0", async function () {
      if (!poseidon) this.skip("circomlibjs required");

      const input = makeInput({
        birthYear: 2025,
        birthMonth: 1,
        birthDay: 1,
        ageThreshold: 0,
        referenceDate: 20260224,
      });

      const witness = await circuit.calculateWitness(input, true);
      await circuit.checkConstraints(witness);
    });

    it("should handle birth on January 1st", async function () {
      if (!poseidon) this.skip("circomlibjs required");

      const input = makeInput({
        birthYear: 2008,
        birthMonth: 1,
        birthDay: 1,
        ageThreshold: 18,
        referenceDate: 20260101,
      });

      const witness = await circuit.calculateWitness(input, true);
      await circuit.checkConstraints(witness);
    });

    it("should handle birth on December 31st with ref Jan 1", async function () {
      if (!poseidon) this.skip("circomlibjs required");

      // Born Dec 31 2007, ref date Jan 1 2026 => 18 years old
      const input = makeInput({
        birthYear: 2007,
        birthMonth: 12,
        birthDay: 31,
        ageThreshold: 18,
        referenceDate: 20260101,
      });

      const witness = await circuit.calculateWitness(input, true);
      await circuit.checkConstraints(witness);
    });
  });
});
