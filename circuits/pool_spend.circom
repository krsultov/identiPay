pragma circom 2.1.6;

include "node_modules/circomlib/circuits/poseidon.circom";
include "node_modules/circomlib/circuits/comparators.circom";
include "node_modules/circomlib/circuits/bitify.circom";

// Merkle tree membership proof for a given depth.
// Verifies that a leaf is included in a Merkle tree with a given root.
template MerkleProof(depth) {
    signal input leaf;
    signal input pathElements[depth];
    signal input pathIndices[depth]; // 0 = left, 1 = right

    signal output root;

    // At each level, hash (left, right) where pathIndices determines
    // which side the current node is on.
    component hashers[depth];
    component indexChecks[depth];

    signal currentHash[depth + 1];
    currentHash[0] <== leaf;

    // Intermediate signals for Merkle path computation
    signal leftDiff[depth];
    signal leftAdjust[depth];
    signal rightDiff[depth];
    signal rightAdjust[depth];

    for (var i = 0; i < depth; i++) {
        // Ensure pathIndices[i] is binary (0 or 1)
        indexChecks[i] = IsZero();
        indexChecks[i].in <== pathIndices[i] * (pathIndices[i] - 1);
        indexChecks[i].out === 1;

        hashers[i] = Poseidon(2);

        // left = currentHash[i] + pathIndices[i] * (pathElements[i] - currentHash[i])
        leftDiff[i] <== pathElements[i] - currentHash[i];
        leftAdjust[i] <== pathIndices[i] * leftDiff[i];
        hashers[i].inputs[0] <== currentHash[i] + leftAdjust[i];

        // right = pathElements[i] + pathIndices[i] * (currentHash[i] - pathElements[i])
        rightDiff[i] <== currentHash[i] - pathElements[i];
        rightAdjust[i] <== pathIndices[i] * rightDiff[i];
        hashers[i].inputs[1] <== pathElements[i] + rightAdjust[i];

        currentHash[i + 1] <== hashers[i].out;
    }

    root <== currentHash[depth];
}

// Pool Spend Circuit (~35K constraints)
//
// Proves the right to spend from a shielded pool note without
// revealing the note's owner or amount. Supports partial withdrawals
// by producing a change commitment.
//
// Private inputs:
//   noteAmount     - the amount stored in the note
//   ownerKey       - the owner's secret key
//   salt           - randomness used when creating the note
//   pathElements   - Merkle proof sibling hashes (depth 20)
//   pathIndices    - Merkle proof path directions (depth 20)
//
// Public inputs:
//   merkleRoot       - the current Merkle tree root
//   nullifier        - unique nullifier to prevent double-spending
//   recipient        - the recipient address/identifier
//   withdrawAmount   - the amount being withdrawn
//   changeCommitment - commitment for the remaining change note

template PoolSpend(depth) {
    // Private inputs
    signal input noteAmount;
    signal input ownerKey;
    signal input salt;
    signal input pathElements[depth];
    signal input pathIndices[depth];

    // Public inputs
    signal input merkleRoot;
    signal input nullifier;
    signal input recipient;
    signal input withdrawAmount;
    signal input changeCommitment;

    // -------------------------------------------------------
    // 1. Compute note commitment = Poseidon(noteAmount, ownerKey, salt)
    // -------------------------------------------------------
    component commitHasher = Poseidon(3);
    commitHasher.inputs[0] <== noteAmount;
    commitHasher.inputs[1] <== ownerKey;
    commitHasher.inputs[2] <== salt;

    signal commitment;
    commitment <== commitHasher.out;

    // -------------------------------------------------------
    // 2. Verify Merkle proof: commitment is in the tree with merkleRoot
    // -------------------------------------------------------
    component merkleProof = MerkleProof(depth);
    merkleProof.leaf <== commitment;
    for (var i = 0; i < depth; i++) {
        merkleProof.pathElements[i] <== pathElements[i];
        merkleProof.pathIndices[i] <== pathIndices[i];
    }
    merkleProof.root === merkleRoot;

    // -------------------------------------------------------
    // 3. Compute nullifier = Poseidon(commitment, ownerKey)
    //    This is deterministic per note+owner, preventing double-spends.
    // -------------------------------------------------------
    component nullifierHasher = Poseidon(2);
    nullifierHasher.inputs[0] <== commitment;
    nullifierHasher.inputs[1] <== ownerKey;
    nullifierHasher.out === nullifier;

    // -------------------------------------------------------
    // 4. Range check: withdrawAmount <= noteAmount (64-bit)
    //    Prove that noteAmount - withdrawAmount >= 0 by decomposing
    //    the difference into 64 bits.
    // -------------------------------------------------------
    signal changeAmount;
    changeAmount <== noteAmount - withdrawAmount;

    // Decompose changeAmount into 64 bits to prove it's non-negative
    component changeRangeCheck = Num2Bits(64);
    changeRangeCheck.in <== changeAmount;

    // Also range-check withdrawAmount to 64 bits
    component withdrawRangeCheck = Num2Bits(64);
    withdrawRangeCheck.in <== withdrawAmount;

    // -------------------------------------------------------
    // 5. Change commitment validation
    //    If changeAmount > 0, changeCommitment must be valid
    //    If changeAmount == 0, changeCommitment must be 0
    // -------------------------------------------------------
    component isChangeZero = IsZero();
    isChangeZero.in <== changeAmount;

    // When changeAmount == 0: isChangeZero.out == 1, so changeCommitment must be 0
    // When changeAmount > 0: isChangeZero.out == 0, changeCommitment can be anything non-zero
    //
    // Constraint: isChangeZero.out * changeCommitment === 0
    // This ensures: if change is 0, then changeCommitment must be 0.
    signal zeroCheck;
    zeroCheck <== isChangeZero.out * changeCommitment;
    zeroCheck === 0;

    // When change > 0, ensure changeCommitment is non-zero
    // (1 - isChangeZero.out) * (1 - isChangeCommitmentZero.out) === (1 - isChangeZero.out)
    // Simplifies to: if change > 0 then changeCommitment != 0
    component isChangeCommitmentZero = IsZero();
    isChangeCommitmentZero.in <== changeCommitment;

    signal hasChange;
    hasChange <== 1 - isChangeZero.out;

    signal hasCommitment;
    hasCommitment <== 1 - isChangeCommitmentZero.out;

    // If hasChange == 1, then hasCommitment must be 1
    // hasChange * (1 - hasCommitment) === 0
    signal changeNeedsCommitment;
    changeNeedsCommitment <== hasChange * (1 - hasCommitment);
    changeNeedsCommitment === 0;

    // -------------------------------------------------------
    // 6. Bind recipient to the proof (prevent front-running)
    // -------------------------------------------------------
    signal recipientSquared;
    recipientSquared <== recipient * recipient;
}

component main {public [merkleRoot, nullifier, recipient, withdrawAmount, changeCommitment]} = PoolSpend(20);
