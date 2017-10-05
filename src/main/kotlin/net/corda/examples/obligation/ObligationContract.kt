package net.corda.examples.obligation

import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.security.PublicKey

class ObligationContract : Contract {

    companion object {
        @JvmStatic
        val OBLIGATION_CONTRACT_ID = "net.corda.examples.obligation.ObligationContract"
    }

    interface Commands : CommandData {
        class Issue : TypeOnlyCommandData(), Commands
//        class Transfer : TypeOnlyCommandData(), Commands
//        class Settle : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction): Unit {
        val command = tx.commands.requireSingleCommand<Commands>()
        when (command.value) {
            is Commands.Issue -> verifyIssue(tx, command.signers)
            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

    private fun verifyIssue(tx: LedgerTransaction, signers: List<PublicKey>) = requireThat {
        "No inputs should be consumed when issuing an obligation." using (tx.inputStates.isEmpty())
        "Only one output state should be created when issuing an obligation." using (tx.outputStates.size == 1)
        val iou = tx.outputStates.single() as Obligation
        "A newly issued obligation must have a positive amount." using (iou.amount > Amount(0, iou.amount.token))
        "The lender and borrower cannot be the same identity." using (iou.borrower != iou.lender)
        "Both lender and borrower together only may sign obligation issue transaction." using
                (signers.toSet() == iou.participants.map { it.owningKey }.toSet())
    }

//    private fun verifyTransfer(tx: LedgerTransaction, signers: List<PublicKey>) {
//        requireThat {
//            "An IOU transfer transaction should only consume one input state." using (tx.inputs.size == 1)
//            "An IOU transfer transaction should only create one output state." using (tx.outputs.size == 1)
//            val input = tx.inputs.single() as Obligation
//            val output = tx.outputs.single() as Obligation
//            "Only the lender property may change." using (input == output.withNewLender(input.lender))
//            "The lender property must change in a transfer." using (input.lender != output.lender)
//            "The borrower, old lender and new lender only must sign an IOU transfer transaction" using
//                    (signers.toSet() == (input.participants.map { it.owningKey }.toSet() `union`
//                            output.participants.map { it.owningKey }.toSet()))
//        }
//    }
//
//    private fun verifySettle(tx: LedgerTransaction, signers: List<PublicKey>) {
//        // Check there is only one group of IOUs and that there is always an input IOU.
//        val ious = tx.groupStates<Obligation, UniqueIdentifier> { it.linearId }.single()
//        require(ious.inputs.size == 1) { "There must be one input IOU." }
//
//        // Check there are output cash states.
//        val cash = tx.outputs.filterIsInstance<Cash.State>()
//        require(cash.isNotEmpty()) { "There must be output cash." }
//
//        // Check that the cash is being assigned to us.
//        val inputIou = ious.inputs.single()
//        val acceptableCash = cash.filter { it.owner == inputIou.lender }
//        require(acceptableCash.isNotEmpty()) { "There must be output cash paid to the recipient." }
//
//        // Sum the cash being sent to us (we don't care about the issuer).
//        val sumAcceptableCash = acceptableCash.sumCash().withoutIssuer()
//        val amountOutstanding = inputIou.amount - inputIou.paid
//        require(amountOutstanding >= sumAcceptableCash) { "The amount settled cannot be more than the amount outstanding." }
//
//        // Check to see if we need an output IOU or not.
//        if (amountOutstanding == sumAcceptableCash) {
//            // If the IOU has been fully settled then there should be no IOU output state.
//            require(ious.outputs.isEmpty()) { "There must be no output IOU as it has been fully settled." }
//        } else {
//            // If the IOU has been partially settled then it should still exist.
//            require(ious.outputs.size == 1) { "There must be one output IOU." }
//
//            // Check only the paid property changes.
//            val outputIou = ious.outputs.single()
//            requireThat {
//                "The amount may not change when settling." using (inputIou.amount == outputIou.amount)
//                "The borrower may not change when settling." using (inputIou.borrower == outputIou.borrower)
//                "The lender may not change when settling." using (inputIou.lender == outputIou.lender)
//                "The linearId may not change when settling." using (inputIou.linearId == outputIou.linearId)
//            }
//
//            // Check the paid property is updated correctly.
//            require(outputIou.paid == inputIou.paid + sumAcceptableCash) { "Paid property incorrectly updated." }
//        }
//
//        // Checks the required parties have signed.
//        "Both lender and borrower together only must sign IOU settle transaction." using
//                (signers.toSet() == inputIou.participants.map { it.owningKey }.toSet())
//    }
}
