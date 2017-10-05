//package net.corda.examples.obligation
//
//import net.corda.core.utilities.ALICE
//import net.corda.core.utilities.BOB
//import net.corda.core.utilities.CHARLIE
//import net.corda.testing.*
//
//
//class IOUTransferTests {
//    // A pre-made dummy state we may need for some of the tests.
//    class DummyState : net.corda.core.contracts.ContractState {
//        override val contract get() = DUMMY_PROGRAM_ID
//        override val participants: List<net.corda.core.identity.AbstractParty> get() = listOf()
//    }
//    // A dummy command.
//    class DummyCommand : net.corda.core.contracts.CommandData
//
//    @org.junit.Test
//    fun mustHandleMultipleCommandValues() {
//        val iou = Obligation(1.POUNDS, ALICE, BOB)
//        ledger {
//            transaction {
//                output { iou }
//                command(ALICE_PUBKEY, BOB_PUBKEY) { DummyCommand() }
//                this `fails with` "Required ObligationContract.Commands command"
//            }
//            transaction {
//                output { iou }
//                command(ALICE_PUBKEY, BOB_PUBKEY) { ObligationContract.Commands.Issue() }
//                this.verifies()
//            }
//            transaction {
//                input { iou }
//                output { iou.withNewLender(CHARLIE) }
//                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
//                this.verifies()
//            }
//        }
//    }
//
//    @org.junit.Test
//    fun mustHaveOneInputAndOneOutput() {
//        val iou = Obligation(1.POUNDS, ALICE, BOB)
//        ledger {
//            transaction {
//                input { iou }
//                input { DummyState() }
//                output { iou.withNewLender(CHARLIE) }
//                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
//                this `fails with` "An IOU transfer transaction should only consume one input state."
//            }
//            transaction {
//                output { iou }
//                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
//                this `fails with` "An IOU transfer transaction should only consume one input state."
//            }
//            transaction {
//                input { iou }
//                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
//                this `fails with` "An IOU transfer transaction should only create one output state."
//            }
//            transaction {
//                input { iou }
//                output { iou.withNewLender(CHARLIE) }
//                output { DummyState() }
//                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
//                this `fails with` "An IOU transfer transaction should only create one output state."
//            }
//            transaction {
//                input { iou }
//                output { iou.withNewLender(CHARLIE) }
//                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
//                this.verifies()
//            }
//        }
//    }
//
//    @org.junit.Test
//    fun onlyTheLenderMayChange() {
//        val iou = Obligation(1.POUNDS, ALICE, BOB)
//        ledger {
//            transaction {
//                input { Obligation(10.DOLLARS, ALICE, BOB) }
//                output { Obligation(1.DOLLARS, ALICE, BOB) }
//                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
//                this `fails with` "Only the lender property may change."
//            }
//            transaction {
//                input { Obligation(10.DOLLARS, ALICE, BOB) }
//                output { Obligation(10.DOLLARS, ALICE, CHARLIE) }
//                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
//                this `fails with` "Only the lender property may change."
//            }
//            transaction {
//                input { Obligation(10.DOLLARS, ALICE, BOB, 5.DOLLARS) }
//                output { Obligation(10.DOLLARS, ALICE, BOB, 10.DOLLARS) }
//                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
//                this `fails with` "Only the lender property may change."
//            }
//            transaction {
//                input { iou }
//                output { iou.withNewLender(CHARLIE) }
//                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
//                this.verifies()
//            }
//        }
//    }
//
//    @org.junit.Test
//    fun theLenderMustChange() {
//        val iou = Obligation(1.POUNDS, ALICE, BOB)
//        ledger {
//            transaction {
//                input { iou }
//                output { iou }
//                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
//                this `fails with` "The lender property must change in a transfer."
//            }
//            transaction {
//                input { iou }
//                output { iou.withNewLender(CHARLIE) }
//                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
//                this.verifies()
//            }
//        }
//    }
//
//    @org.junit.Test
//    fun allParticipantsMustSign() {
//        val iou = Obligation(1.POUNDS, ALICE, BOB)
//        ledger {
//            transaction {
//                input { iou }
//                output { iou.withNewLender(CHARLIE) }
//                command(ALICE_PUBKEY, BOB_PUBKEY) { ObligationContract.Commands.Transfer() }
//                this `fails with` "The borrower, old lender and new lender only must sign an IOU transfer transaction"
//            }
//            transaction {
//                input { iou }
//                output { iou.withNewLender(CHARLIE) }
//                command(ALICE_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
//                this `fails with` "The borrower, old lender and new lender only must sign an IOU transfer transaction"
//            }
//            transaction {
//                input { iou }
//                output { iou.withNewLender(CHARLIE) }
//                command(BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
//                this `fails with` "The borrower, old lender and new lender only must sign an IOU transfer transaction"
//            }
//            transaction {
//                input { iou }
//                output { iou.withNewLender(CHARLIE) }
//                command(ALICE_PUBKEY, BOB_PUBKEY, MINI_CORP_PUBKEY) { ObligationContract.Commands.Transfer() }
//                this `fails with` "The borrower, old lender and new lender only must sign an IOU transfer transaction"
//            }
//            transaction {
//                input { iou }
//                output { iou.withNewLender(CHARLIE) }
//                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY, MINI_CORP_PUBKEY) { ObligationContract.Commands.Transfer() }
//                this `fails with` "The borrower, old lender and new lender only must sign an IOU transfer transaction"
//            }
//            transaction {
//                input { iou }
//                output { iou.withNewLender(CHARLIE) }
//                command(ALICE_PUBKEY, BOB_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Transfer() }
//                this.verifies()
//            }
//        }
//    }
//}