//package net.corda.examples.obligation
//
//import net.corda.core.utilities.ALICE
//import net.corda.core.utilities.BOB
//import net.corda.core.utilities.DUMMY_PUBKEY_1
//import net.corda.testing.ALICE_PUBKEY
//import net.corda.testing.BOB_PUBKEY
//import net.corda.testing.MINI_CORP_PUBKEY
//
//class IOUIssueTests {
//    // A pre-made dummy state we may need for some of the tests.
//    val dummyState = object : net.corda.core.contracts.ContractState {
//        override val contract get() = DUMMY_PROGRAM_ID
//        override val participants: List<net.corda.core.identity.AbstractParty> get() = listOf()
//    }
//    // A pre-defined dummy command.
//    class DummyCommand : net.corda.core.contracts.TypeOnlyCommandData()
//
//    @org.junit.Test
//    fun mustIncludeIssueCommand() {
//        val iou = Obligation(1.POUNDS, ALICE, BOB)
//        net.corda.testing.ledger {
//            transaction {
//                output { iou }
//                this.fails()
//            }
//            transaction {
//                output { iou }
//                command(ALICE_PUBKEY, BOB_PUBKEY) { DummyCommand() } // Wrong type.
//                this.fails()
//            }
//            transaction {
//                output { iou }
//                command(ALICE_PUBKEY, BOB_PUBKEY) { net.corda.samples.obligation.ObligationContract.Commands.Issue() } // Correct type.
//                this.verifies()
//            }
//        }
//    }
//
//    @org.junit.Test
//    fun issueTransactionMustHaveNoInputs() {
//        val iou = Obligation(1.POUNDS, ALICE, BOB)
//        net.corda.testing.ledger {
//            transaction {
//                input { dummyState }
//                command(ALICE_PUBKEY, BOB_PUBKEY) { net.corda.samples.obligation.ObligationContract.Commands.Issue() }
//                output { iou }
//                this `fails with` "No inputs should be consumed when issuing an IOU."
//            }
//            transaction {
//                output { iou }
//                command(ALICE_PUBKEY, BOB_PUBKEY) { net.corda.samples.obligation.ObligationContract.Commands.Issue() }
//                this.verifies() // As there are no input states.
//            }
//        }
//    }
//
//    @org.junit.Test
//    fun issueTransactionMustHaveOneOutput() {
//        val iou = Obligation(1.POUNDS, ALICE, BOB)
//        net.corda.testing.ledger {
//            transaction {
//                command(ALICE_PUBKEY, BOB_PUBKEY) { net.corda.samples.obligation.ObligationContract.Commands.Issue() }
//                output { iou } // Two outputs fails.
//                output { iou }
//                this `fails with` "Only one output state should be created when issuing an IOU."
//            }
//            transaction {
//                command(ALICE_PUBKEY, BOB_PUBKEY) { net.corda.samples.obligation.ObligationContract.Commands.Issue() }
//                output { iou } // One output passes.
//                this.verifies()
//            }
//        }
//    }
//
//    @org.junit.Test
//    fun cannotCreateZeroValueIOUs() {
//        net.corda.testing.ledger {
//            transaction {
//                command(ALICE_PUBKEY, BOB_PUBKEY) { net.corda.samples.obligation.ObligationContract.Commands.Issue() }
//                output { Obligation(0.POUNDS, ALICE, BOB) } // Zero amount fails.
//                this `fails with` "A newly issued IOU must have a positive amount."
//            }
//            transaction {
//                command(ALICE_PUBKEY, BOB_PUBKEY) { net.corda.samples.obligation.ObligationContract.Commands.Issue() }
//                output { Obligation(100.SWISS_FRANCS, ALICE, BOB) }
//                this.verifies()
//            }
//            transaction {
//                command(ALICE_PUBKEY, BOB_PUBKEY) { net.corda.samples.obligation.ObligationContract.Commands.Issue() }
//                output { Obligation(1.POUNDS, ALICE, BOB) }
//                this.verifies()
//            }
//            transaction {
//                command(ALICE_PUBKEY, BOB_PUBKEY) { net.corda.samples.obligation.ObligationContract.Commands.Issue() }
//                output { Obligation(10.DOLLARS, ALICE, BOB) }
//                this.verifies()
//            }
//        }
//    }
//
//    @org.junit.Test
//    fun lenderAndBorrowerCannotBeTheSame() {
//        val iou = Obligation(1.POUNDS, ALICE, BOB)
//        val borrowerIsLenderIou = Obligation(10.POUNDS, ALICE, ALICE)
//        net.corda.testing.ledger {
//            transaction {
//                command(ALICE_PUBKEY, BOB_PUBKEY) { net.corda.samples.obligation.ObligationContract.Commands.Issue() }
//                output { borrowerIsLenderIou }
//                this `fails with` "The lender and borrower cannot be the same identity."
//            }
//            transaction {
//                command(ALICE_PUBKEY, BOB_PUBKEY) { net.corda.samples.obligation.ObligationContract.Commands.Issue() }
//                output { iou }
//                this.verifies()
//            }
//        }
//    }
//
//    @org.junit.Test
//    fun lenderAndBorrowerMustSignIssueTransaction() {
//        val iou = Obligation(1.POUNDS, ALICE, BOB)
//        net.corda.testing.ledger {
//            transaction {
//                command(DUMMY_PUBKEY_1) { net.corda.samples.obligation.ObligationContract.Commands.Issue() }
//                output { iou }
//                this `fails with` "Both lender and borrower together only may sign IOU issue transaction."
//            }
//            transaction {
//                command(ALICE_PUBKEY) { net.corda.samples.obligation.ObligationContract.Commands.Issue() }
//                output { iou }
//                this `fails with` "Both lender and borrower together only may sign IOU issue transaction."
//            }
//            transaction {
//                command(BOB_PUBKEY) { net.corda.samples.obligation.ObligationContract.Commands.Issue() }
//                output { iou }
//                this `fails with` "Both lender and borrower together only may sign IOU issue transaction."
//            }
//            transaction {
//                command(BOB_PUBKEY, BOB_PUBKEY, BOB_PUBKEY) { net.corda.samples.obligation.ObligationContract.Commands.Issue() }
//                output { iou }
//                this `fails with` "Both lender and borrower together only may sign IOU issue transaction."
//            }
//            transaction {
//                command(BOB_PUBKEY, BOB_PUBKEY, MINI_CORP_PUBKEY, ALICE_PUBKEY) { net.corda.samples.obligation.ObligationContract.Commands.Issue() }
//                output { iou }
//                this `fails with` "Both lender and borrower together only may sign IOU issue transaction."
//            }
//            transaction {
//                command(BOB_PUBKEY, BOB_PUBKEY, BOB_PUBKEY, ALICE_PUBKEY) { net.corda.samples.obligation.ObligationContract.Commands.Issue() }
//                output { iou }
//                this.verifies()
//            }
//            transaction {
//                command(ALICE_PUBKEY, BOB_PUBKEY) { net.corda.samples.obligation.ObligationContract.Commands.Issue() }
//                output { iou }
//                this.verifies()
//            }
//        }
//    }
//}