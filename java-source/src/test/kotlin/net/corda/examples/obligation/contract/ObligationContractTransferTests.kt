package net.corda.examples.obligation.contract

import net.corda.examples.obligation.Obligation
import net.corda.examples.obligation.ObligationContract
import net.corda.examples.obligation.ObligationContract.OBLIGATION_CONTRACT_ID
import net.corda.finance.DOLLARS
import net.corda.testing.*
import net.corda.testing.contracts.DUMMY_PROGRAM_ID
import org.junit.Test

class ObligationContractTransferTests : ObligationContractUnitTests() {

    @Test
    fun `must handle multiple command values`() {
        ledger {
            transaction {
                output(OBLIGATION_CONTRACT_ID) { oneDollarObligation }
                command(ALICE_PUBKEY, BOB_PUBKEY) { DummyCommand() }
                this `fails with` "List is empty."
            }
            transaction {
                output(OBLIGATION_CONTRACT_ID) { oneDollarObligation }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ObligationContract.Commands.Issue() }
                this.verifies()
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { oneDollarObligation }
                output(OBLIGATION_CONTRACT_ID) { oneDollarObligation.withNewLender(CHARLIE) }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
                this.verifies()
            }
        }
    }

    @Test
    fun `must have one input and one output`() {
        ledger {
            transaction {
                input(OBLIGATION_CONTRACT_ID) { oneDollarObligation }
                input(DUMMY_PROGRAM_ID) { DummyState() }
                output(OBLIGATION_CONTRACT_ID) { oneDollarObligation.withNewLender(CHARLIE) }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
                this `fails with` "An obligation transfer transaction should only consume one input state."
            }
            transaction {
                output(OBLIGATION_CONTRACT_ID) { oneDollarObligation }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
                this `fails with` "An obligation transfer transaction should only consume one input state."
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { oneDollarObligation }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
                this `fails with` "An obligation transfer transaction should only create one output state."
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { oneDollarObligation }
                output(OBLIGATION_CONTRACT_ID) { oneDollarObligation.withNewLender(CHARLIE) }
                output(OBLIGATION_CONTRACT_ID) { DummyState() }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
                this `fails with` "An obligation transfer transaction should only create one output state."
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { oneDollarObligation }
                output(OBLIGATION_CONTRACT_ID) { oneDollarObligation.withNewLender(CHARLIE) }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
                this.verifies()
            }
        }
    }

    @Test
    fun `only the lender may change`() {
        ledger {
            transaction {
                input(OBLIGATION_CONTRACT_ID) { Obligation(10.DOLLARS, ALICE, BOB) }
                output(OBLIGATION_CONTRACT_ID) { Obligation(1.DOLLARS, ALICE, BOB) }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
                this `fails with` "Only the lender property may change."
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { Obligation(10.DOLLARS, ALICE, BOB) }
                output(OBLIGATION_CONTRACT_ID) { Obligation(10.DOLLARS, ALICE, CHARLIE) }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
                this `fails with` "Only the lender property may change."
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { Obligation(10.DOLLARS, ALICE, BOB, 5.DOLLARS) }
                output(OBLIGATION_CONTRACT_ID) { Obligation(10.DOLLARS, ALICE, BOB, 10.DOLLARS) }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
                this `fails with` "Only the lender property may change."
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { oneDollarObligation }
                output(OBLIGATION_CONTRACT_ID) { oneDollarObligation.withNewLender(CHARLIE) }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
                this.verifies()
            }
        }
    }

    @Test
    fun `the lender must change`() {
        ledger {
            transaction {
                input(OBLIGATION_CONTRACT_ID) { oneDollarObligation }
                output(OBLIGATION_CONTRACT_ID) { oneDollarObligation }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
                this `fails with` "The lender property must change in a transfer."
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { oneDollarObligation }
                output(OBLIGATION_CONTRACT_ID) { oneDollarObligation.withNewLender(CHARLIE) }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
                this.verifies()
            }
        }
    }

    @Test
    fun `all participants must sign`() {
        ledger {
            transaction {
                input(OBLIGATION_CONTRACT_ID) { oneDollarObligation }
                output(OBLIGATION_CONTRACT_ID) { oneDollarObligation.withNewLender(CHARLIE) }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ObligationContract.Commands.Transfer() }
                this `fails with` "The borrower, old lender and new lender only must sign an obligation transfer transaction"
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { oneDollarObligation }
                output(OBLIGATION_CONTRACT_ID) { oneDollarObligation.withNewLender(CHARLIE) }
                command(ALICE_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
                this `fails with` "The borrower, old lender and new lender only must sign an obligation transfer transaction"
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { oneDollarObligation }
                output(OBLIGATION_CONTRACT_ID) { oneDollarObligation.withNewLender(CHARLIE) }
                command(BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
                this `fails with` "The borrower, old lender and new lender only must sign an obligation transfer transaction"
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { oneDollarObligation }
                output(OBLIGATION_CONTRACT_ID) { oneDollarObligation.withNewLender(CHARLIE) }
                command(ALICE_PUBKEY, BOB_PUBKEY, MINI_CORP_PUBKEY) { ObligationContract.Commands.Transfer() }
                this `fails with` "The borrower, old lender and new lender only must sign an obligation transfer transaction"
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { oneDollarObligation }
                output(OBLIGATION_CONTRACT_ID) { oneDollarObligation.withNewLender(CHARLIE) }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY, MINI_CORP_PUBKEY) { ObligationContract.Commands.Transfer() }
                this `fails with` "The borrower, old lender and new lender only must sign an obligation transfer transaction"
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { oneDollarObligation }
                output(OBLIGATION_CONTRACT_ID) { oneDollarObligation.withNewLender(CHARLIE) }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
                this.verifies()
            }
        }
    }
}
