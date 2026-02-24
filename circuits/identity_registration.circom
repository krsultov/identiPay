pragma circom 2.1.6;

include "node_modules/circomlib/circuits/poseidon.circom";

// Identity Registration Circuit (~700 constraints)
//
// Computes a Poseidon commitment over identity attributes.
// The commitment hides the user's document details behind a salt
// while allowing on-chain verification that the commitment was
// correctly formed.
//
// Private inputs:
//   issuerCertHash  - hash of the issuing authority's certificate
//   docNumberHash   - hash of the document number
//   dobHash         - hash of the date of birth (year, month, day)
//   userSalt        - random salt chosen by the user
//
// Public output:
//   identityCommitment - Poseidon(issuerCertHash, docNumberHash, dobHash, userSalt)

template IdentityRegistration() {
    // Private inputs
    signal input issuerCertHash;
    signal input docNumberHash;
    signal input dobHash;
    signal input userSalt;

    // Public output
    signal output identityCommitment;

    // Compute Poseidon hash over all four private inputs
    component hasher = Poseidon(4);
    hasher.inputs[0] <== issuerCertHash;
    hasher.inputs[1] <== docNumberHash;
    hasher.inputs[2] <== dobHash;
    hasher.inputs[3] <== userSalt;

    // Constrain the output
    identityCommitment <== hasher.out;
}

component main = IdentityRegistration();
