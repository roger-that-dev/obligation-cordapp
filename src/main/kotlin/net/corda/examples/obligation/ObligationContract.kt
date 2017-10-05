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

}
