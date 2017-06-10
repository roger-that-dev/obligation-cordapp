package net.corda.iou.contract

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.BOB
import net.corda.core.utilities.DUMMY_PUBKEY_1
import net.corda.iou.state.IOUState
import net.corda.testing.ALICE_PUBKEY
import net.corda.testing.BOB_PUBKEY
import net.corda.testing.MINI_CORP_PUBKEY
import net.corda.testing.ledger
import org.junit.Test

class IOUIssueTests {
    // A pre-made dummy state we may need for some of the tests.
    val dummyState = object : ContractState {
        override val contract get() = DUMMY_PROGRAM_ID
        override val participants: List<AbstractParty> get() = listOf()
    }
    // A pre-defined dummy command.
    class DummyCommand : TypeOnlyCommandData()

    @Test
    fun mustIncludeIssueCommand() {
        val iou = IOUState(1.POUNDS, ALICE, BOB)
        ledger {
            transaction {
                output { iou }
                this.fails()
            }
            transaction {
                output { iou }
                command(ALICE_PUBKEY, BOB_PUBKEY) { DummyCommand() } // Wrong type.
                this.fails()
            }
            transaction {
                output { iou }
                command(ALICE_PUBKEY, BOB_PUBKEY) { IOUContract.Commands.Issue() } // Correct type.
                this.verifies()
            }
        }
    }

    @Test
    fun issueTransactionMustHaveNoInputs() {
        val iou = IOUState(1.POUNDS, ALICE, BOB)
        ledger {
            transaction {
                input { dummyState }
                command(ALICE_PUBKEY, BOB_PUBKEY) { IOUContract.Commands.Issue() }
                output { iou }
                this `fails with` "No inputs should be consumed when issuing an IOU."
            }
            transaction {
                output { iou }
                command(ALICE_PUBKEY, BOB_PUBKEY) { IOUContract.Commands.Issue() }
                this.verifies() // As there are no input states.
            }
        }
    }

    @Test
    fun issueTransactionMustHaveOneOutput() {
        val iou = IOUState(1.POUNDS, ALICE, BOB)
        ledger {
            transaction {
                command(ALICE_PUBKEY, BOB_PUBKEY) { IOUContract.Commands.Issue() }
                output { iou } // Two outputs fails.
                output { iou }
                this `fails with` "Only one output state should be created when issuing an IOU."
            }
            transaction {
                command(ALICE_PUBKEY, BOB_PUBKEY) { IOUContract.Commands.Issue() }
                output { iou } // One output passes.
                this.verifies()
            }
        }
    }

    @Test
    fun cannotCreateZeroValueIOUs() {
        ledger {
            transaction {
                command(ALICE_PUBKEY, BOB_PUBKEY) { IOUContract.Commands.Issue() }
                output { IOUState(0.POUNDS, ALICE, BOB) } // Zero amount fails.
                this `fails with` "A newly issued IOU must have a positive amount."
            }
            transaction {
                command(ALICE_PUBKEY, BOB_PUBKEY) { IOUContract.Commands.Issue() }
                output { IOUState(100.SWISS_FRANCS, ALICE, BOB) }
                this.verifies()
            }
            transaction {
                command(ALICE_PUBKEY, BOB_PUBKEY) { IOUContract.Commands.Issue() }
                output { IOUState(1.POUNDS, ALICE, BOB) }
                this.verifies()
            }
            transaction {
                command(ALICE_PUBKEY, BOB_PUBKEY) { IOUContract.Commands.Issue() }
                output { IOUState(10.DOLLARS, ALICE, BOB) }
                this.verifies()
            }
        }
    }

    @Test
    fun lenderAndBorrowerCannotBeTheSame() {
        val iou = IOUState(1.POUNDS, ALICE, BOB)
        val borrowerIsLenderIou = IOUState(10.POUNDS, ALICE, ALICE)
        ledger {
            transaction {
                command(ALICE_PUBKEY, BOB_PUBKEY) { IOUContract.Commands.Issue() }
                output { borrowerIsLenderIou }
                this `fails with` "The lender and borrower cannot be the same identity."
            }
            transaction {
                command(ALICE_PUBKEY, BOB_PUBKEY) { IOUContract.Commands.Issue() }
                output { iou }
                this.verifies()
            }
        }
    }

    @Test
    fun lenderAndBorrowerMustSignIssueTransaction() {
        val iou = IOUState(1.POUNDS, ALICE, BOB)
        ledger {
            transaction {
                command(DUMMY_PUBKEY_1) { IOUContract.Commands.Issue() }
                output { iou }
                this `fails with` "Both lender and borrower together only may sign IOU issue transaction."
            }
            transaction {
                command(ALICE_PUBKEY) { IOUContract.Commands.Issue() }
                output { iou }
                this `fails with` "Both lender and borrower together only may sign IOU issue transaction."
            }
            transaction {
                command(BOB_PUBKEY) { IOUContract.Commands.Issue() }
                output { iou }
                this `fails with` "Both lender and borrower together only may sign IOU issue transaction."
            }
            transaction {
                command(BOB_PUBKEY, BOB_PUBKEY, BOB_PUBKEY) { IOUContract.Commands.Issue() }
                output { iou }
                this `fails with` "Both lender and borrower together only may sign IOU issue transaction."
            }
            transaction {
                command(BOB_PUBKEY, BOB_PUBKEY, MINI_CORP_PUBKEY, ALICE_PUBKEY) { IOUContract.Commands.Issue() }
                output { iou }
                this `fails with` "Both lender and borrower together only may sign IOU issue transaction."
            }
            transaction {
                command(BOB_PUBKEY, BOB_PUBKEY, BOB_PUBKEY, ALICE_PUBKEY) { IOUContract.Commands.Issue() }
                output { iou }
                this.verifies()
            }
            transaction {
                command(ALICE_PUBKEY, BOB_PUBKEY) { IOUContract.Commands.Issue() }
                output { iou }
                this.verifies()
            }
        }
    }
}