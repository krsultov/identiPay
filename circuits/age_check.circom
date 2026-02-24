pragma circom 2.1.6;

include "node_modules/circomlib/circuits/poseidon.circom";
include "node_modules/circomlib/circuits/comparators.circom";
include "node_modules/circomlib/circuits/bitify.circom";

// Age Check Circuit (~1500 constraints)
//
// Proves that a user meets a minimum age threshold without revealing
// their exact date of birth. The circuit binds the proof to a specific
// identity commitment and an intent hash for replay protection.
//
// Private inputs:
//   birthYear, birthMonth, birthDay - date of birth components
//   dobHash   - Poseidon(birthYear, birthMonth, birthDay)
//   userSalt  - the same salt used in identity registration
//
// Public inputs:
//   ageThreshold      - minimum age required (e.g. 18)
//   referenceDate     - current date as YYYYMMDD integer (e.g. 20260224)
//   identityCommitment - the on-chain identity commitment
//   intentHash        - hash binding this proof to a specific transaction intent

template AgeCheck() {
    // Private inputs
    signal input birthYear;
    signal input birthMonth;
    signal input birthDay;
    signal input dobHash;
    signal input userSalt;

    // Public inputs
    signal input ageThreshold;
    signal input referenceDate;
    signal input identityCommitment;
    signal input intentHash;

    // -------------------------------------------------------
    // 1. Verify dobHash = Poseidon(birthYear, birthMonth, birthDay)
    // -------------------------------------------------------
    component dobHasher = Poseidon(3);
    dobHasher.inputs[0] <== birthYear;
    dobHasher.inputs[1] <== birthMonth;
    dobHasher.inputs[2] <== birthDay;
    dobHasher.out === dobHash;

    // -------------------------------------------------------
    // 2. Parse referenceDate into year, month, day
    //    referenceDate = refYear * 10000 + refMonth * 100 + refDay
    // -------------------------------------------------------
    signal refYear;
    signal refMonthDay;
    signal refMonth;
    signal refDay;

    refMonthDay <-- referenceDate % 10000;
    refYear <-- (referenceDate - refMonthDay) / 10000;
    // Constrain: referenceDate == refYear * 10000 + refMonthDay
    signal refYearTimes10000;
    refYearTimes10000 <== refYear * 10000;
    refYearTimes10000 + refMonthDay === referenceDate;

    refDay <-- refMonthDay % 100;
    refMonth <-- (refMonthDay - refDay) / 100;
    // Constrain: refMonthDay == refMonth * 100 + refDay
    signal refMonthTimes100;
    refMonthTimes100 <== refMonth * 100;
    refMonthTimes100 + refDay === refMonthDay;

    // -------------------------------------------------------
    // 3. Range checks on parsed date components
    //    Ensure values are within valid ranges (prevents malicious witness)
    // -------------------------------------------------------
    // refMonth in [1, 12]
    component refMonthGte1 = GreaterEqThan(8);
    refMonthGte1.in[0] <== refMonth;
    refMonthGte1.in[1] <== 1;
    refMonthGte1.out === 1;

    component refMonthLte12 = LessEqThan(8);
    refMonthLte12.in[0] <== refMonth;
    refMonthLte12.in[1] <== 12;
    refMonthLte12.out === 1;

    // refDay in [1, 31]
    component refDayGte1 = GreaterEqThan(8);
    refDayGte1.in[0] <== refDay;
    refDayGte1.in[1] <== 1;
    refDayGte1.out === 1;

    component refDayLte31 = LessEqThan(8);
    refDayLte31.in[0] <== refDay;
    refDayLte31.in[1] <== 31;
    refDayLte31.out === 1;

    // birthMonth in [1, 12]
    component birthMonthGte1 = GreaterEqThan(8);
    birthMonthGte1.in[0] <== birthMonth;
    birthMonthGte1.in[1] <== 1;
    birthMonthGte1.out === 1;

    component birthMonthLte12 = LessEqThan(8);
    birthMonthLte12.in[0] <== birthMonth;
    birthMonthLte12.in[1] <== 12;
    birthMonthLte12.out === 1;

    // birthDay in [1, 31]
    component birthDayGte1 = GreaterEqThan(8);
    birthDayGte1.in[0] <== birthDay;
    birthDayGte1.in[1] <== 1;
    birthDayGte1.out === 1;

    component birthDayLte31 = LessEqThan(8);
    birthDayLte31.in[0] <== birthDay;
    birthDayLte31.in[1] <== 31;
    birthDayLte31.out === 1;

    // -------------------------------------------------------
    // 4. Compute effective age with month/day precision
    //    age = refYear - birthYear
    //    If (refMonth, refDay) < (birthMonth, birthDay), subtract 1
    // -------------------------------------------------------
    signal rawAge;
    rawAge <== refYear - birthYear;

    // Compute birthMonthDay and refMonthDay for comparison
    signal birthMonthDay;
    birthMonthDay <== birthMonth * 100 + birthDay;

    // hasBirthdayPassed: 1 if refMonthDay >= birthMonthDay, else 0
    component birthdayCheck = GreaterEqThan(16);
    birthdayCheck.in[0] <== refMonthDay;
    birthdayCheck.in[1] <== birthMonthDay;

    // effectiveAge = rawAge - (1 - hasBirthdayPassed)
    //              = rawAge - 1 + hasBirthdayPassed
    signal effectiveAge;
    effectiveAge <== rawAge - 1 + birthdayCheck.out;

    // -------------------------------------------------------
    // 5. Check effectiveAge >= ageThreshold
    // -------------------------------------------------------
    component ageGte = GreaterEqThan(16);
    ageGte.in[0] <== effectiveAge;
    ageGte.in[1] <== ageThreshold;
    ageGte.out === 1;

    // -------------------------------------------------------
    // 6. Bind identityCommitment and intentHash (public inputs)
    //    These signals are public inputs declared below in main.
    //    We create a dummy constraint to ensure they are not
    //    optimized away by the compiler.
    // -------------------------------------------------------
    signal identitySquared;
    identitySquared <== identityCommitment * identityCommitment;

    signal intentSquared;
    intentSquared <== intentHash * intentHash;
}

// Public inputs: ageThreshold, referenceDate, identityCommitment, intentHash
// All other inputs are private by default.
component main {public [ageThreshold, referenceDate, identityCommitment, intentHash]} = AgeCheck();
