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
        class Transfer : TypeOnlyCommandData(), Commands
        class Settle : TypeOnlyCommandData(), Commands
    }

    override fun verify(tx: LedgerTransaction): Unit {
        val command = tx.commands.requireSingleCommand<Commands>()
        val setOfSigners = command.signers.toSet()
        when (command.value) {
            is Commands.Issue -> verifyIssue(tx, setOfSigners)
            is Commands.Transfer -> verifyTransfer(tx, setOfSigners)
            is Commands.Settle -> verifySettle(tx, setOfSigners)
            else -> throw IllegalArgumentException("Unrecognised command.")
        }
    }

    private fun keysFromParticipants(obligation: Obligation): Set<PublicKey> {
        return obligation.participants.map {
            it.owningKey
        }.toSet()
    }

    // This only allows one obligation issuance per transaction.
    private fun verifyIssue(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "No inputs should be consumed when issuing an obligation." using (tx.inputStates.isEmpty())
        "Only one output state should be created when issuing an obligation." using (tx.outputStates.size == 1)
        val obligation = tx.outputStates.single() as Obligation
        "A newly issued obligation must have a positive amount." using
                (obligation.amount > Amount(0, obligation.amount.token))
        "The lender and borrower cannot be the same identity." using (obligation.borrower != obligation.lender)
        "Both lender and borrower together only may sign obligation issue transaction." using
                (signers == keysFromParticipants(obligation))
    }

    // This only allows one obligation transfer per transaction.
    private fun verifyTransfer(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "An IOU transfer transaction should only consume one input state." using (tx.inputs.size == 1)
        "An IOU transfer transaction should only create one output state." using (tx.outputs.size == 1)
        val input = tx.inputStates.single() as Obligation
        val output = tx.outputStates.single() as Obligation
        "Only the lender property may change." using (input == output.withNewLender(input.lender))
        "The lender property must change in a transfer." using (input.lender != output.lender)
        "The borrower, old lender and new lender only must sign an IOU transfer transaction" using
                (signers == (keysFromParticipants(input) `union` keysFromParticipants(output)))
    }

    private fun verifySettle(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        // TODO: Complete this.
    }
}