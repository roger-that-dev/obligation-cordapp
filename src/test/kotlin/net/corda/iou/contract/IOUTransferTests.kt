package net.corda.iou.contract

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.BOB
import net.corda.core.utilities.CHARLIE
import net.corda.iou.state.IOUState
import net.corda.testing.*
import org.junit.Test


class IOUTransferTests {
    // A pre-made dummy state we may need for some of the tests.
    class DummyState : ContractState {
        override val contract get() = DUMMY_PROGRAM_ID
        override val participants: List<AbstractParty> get() = listOf()
    }
    // A dummy command.
    class DummyCommand : CommandData

    @Test
    fun mustHandleMultipleCommandValues() {
        val iou = IOUState(1.POUNDS, ALICE, BOB)
        ledger {
            transaction {
                output { iou }
                command(ALICE_PUBKEY, BOB_PUBKEY) { DummyCommand() }
                this `fails with` "Required net.corda.iou.contract.IOUContract.Commands command"
            }
            transaction {
                output { iou }
                command(ALICE_PUBKEY, BOB_PUBKEY) { IOUContract.Commands.Issue() }
                this.verifies()
            }
            transaction {
                input { iou }
                output { iou.withNewLender(CHARLIE) }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { IOUContract.Commands.Transfer() }
                this.verifies()
            }
        }
    }

    @Test
    fun mustHaveOneInputAndOneOutput() {
        val iou = IOUState(1.POUNDS, ALICE, BOB)
        ledger {
            transaction {
                input { iou }
                input { DummyState() }
                output { iou.withNewLender(CHARLIE) }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { IOUContract.Commands.Transfer() }
                this `fails with` "An IOU transfer transaction should only consume one input state."
            }
            transaction {
                output { iou }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { IOUContract.Commands.Transfer() }
                this `fails with` "An IOU transfer transaction should only consume one input state."
            }
            transaction {
                input { iou }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { IOUContract.Commands.Transfer() }
                this `fails with` "An IOU transfer transaction should only create one output state."
            }
            transaction {
                input { iou }
                output { iou.withNewLender(CHARLIE) }
                output { DummyState() }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { IOUContract.Commands.Transfer() }
                this `fails with` "An IOU transfer transaction should only create one output state."
            }
            transaction {
                input { iou }
                output { iou.withNewLender(CHARLIE) }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { IOUContract.Commands.Transfer() }
                this.verifies()
            }
        }
    }

    @Test
    fun onlyTheLenderMayChange() {
        val iou = IOUState(1.POUNDS, ALICE, BOB)
        ledger {
            transaction {
                input { IOUState(10.DOLLARS, ALICE, BOB) }
                output { IOUState(1.DOLLARS, ALICE, BOB) }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { IOUContract.Commands.Transfer() }
                this `fails with` "Only the lender property may change."
            }
            transaction {
                input { IOUState(10.DOLLARS, ALICE, BOB) }
                output { IOUState(10.DOLLARS, ALICE, CHARLIE) }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { IOUContract.Commands.Transfer() }
                this `fails with` "Only the lender property may change."
            }
            transaction {
                input { IOUState(10.DOLLARS, ALICE, BOB, 5.DOLLARS) }
                output { IOUState(10.DOLLARS, ALICE, BOB, 10.DOLLARS) }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { IOUContract.Commands.Transfer() }
                this `fails with` "Only the lender property may change."
            }
            transaction {
                input { iou }
                output { iou.withNewLender(CHARLIE) }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { IOUContract.Commands.Transfer() }
                this.verifies()
            }
        }
    }

    @Test
    fun theLenderMustChange() {
        val iou = IOUState(1.POUNDS, ALICE, BOB)
        ledger {
            transaction {
                input { iou }
                output { iou }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { IOUContract.Commands.Transfer() }
                this `fails with` "The lender property must change in a transfer."
            }
            transaction {
                input { iou }
                output { iou.withNewLender(CHARLIE) }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { IOUContract.Commands.Transfer() }
                this.verifies()
            }
        }
    }

    @Test
    fun allParticipantsMustSign() {
        val iou = IOUState(1.POUNDS, ALICE, BOB)
        ledger {
            transaction {
                input { iou }
                output { iou.withNewLender(CHARLIE) }
                command(ALICE_PUBKEY, BOB_PUBKEY) { IOUContract.Commands.Transfer() }
                this `fails with` "The borrower, old lender and new lender only must sign an IOU transfer transaction"
            }
            transaction {
                input { iou }
                output { iou.withNewLender(CHARLIE) }
                command(ALICE_PUBKEY, CHARLIE_PUBKEY) { IOUContract.Commands.Transfer() }
                this `fails with` "The borrower, old lender and new lender only must sign an IOU transfer transaction"
            }
            transaction {
                input { iou }
                output { iou.withNewLender(CHARLIE) }
                command(BOB_PUBKEY, CHARLIE_PUBKEY) { IOUContract.Commands.Transfer() }
                this `fails with` "The borrower, old lender and new lender only must sign an IOU transfer transaction"
            }
            transaction {
                input { iou }
                output { iou.withNewLender(CHARLIE) }
                command(ALICE_PUBKEY, BOB_PUBKEY, MINI_CORP_PUBKEY) { IOUContract.Commands.Transfer() }
                this `fails with` "The borrower, old lender and new lender only must sign an IOU transfer transaction"
            }
            transaction {
                input { iou }
                output { iou.withNewLender(CHARLIE) }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY, MINI_CORP_PUBKEY) { IOUContract.Commands.Transfer() }
                this `fails with` "The borrower, old lender and new lender only must sign an IOU transfer transaction"
            }
            transaction {
                input { iou }
                output { iou.withNewLender(CHARLIE) }
                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { IOUContract.Commands.Transfer() }
                this.verifies()
            }
        }
    }
}