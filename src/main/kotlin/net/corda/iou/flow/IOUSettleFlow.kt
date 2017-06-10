package net.corda.iou.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.Command
import net.corda.core.contracts.TransactionType
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.linearHeadsOfType
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.flows.CollectSignaturesFlow
import net.corda.flows.FinalityFlow
import net.corda.flows.SignTransactionFlow
import net.corda.iou.contract.IOUContract
import net.corda.iou.state.IOUState
import java.util.*

object IOUSettleFlow {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val linearId: UniqueIdentifier, val amount: Amount<Currency>) : FlowLogic<SignedTransaction>() {

        override val progressTracker: ProgressTracker = Initiator.tracker()

        companion object {
            object PREPARATION : ProgressTracker.Step("Obtaining IOU from vault")
            object BUILDING : ProgressTracker.Step("Building and verifying transaction.")
            object SIGNING : ProgressTracker.Step("signing transaction.")
            object COLLECTING : ProgressTracker.Step("Collecting counterparty signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING : ProgressTracker.Step("Finalising transaction") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(PREPARATION, BUILDING, SIGNING, COLLECTING, FINALISING)
        }

        @Suspendable
        override fun call(): SignedTransaction {
            val me = serviceHub.myInfo.legalIdentity

            // Step 1. Retrieve the IOU state from the vault.
            progressTracker.currentStep = PREPARATION
            val iouStates = serviceHub.vaultService.linearHeadsOfType<IOUState>()
            val iouToSettle = iouStates[linearId] ?: throw IllegalArgumentException("IOUState with linearId $linearId not found.")
            val counterparty = iouToSettle.state.data.lender

            // Step 2. Check the party running this flow is the borrower.
            require(iouToSettle.state.data.borrower == me) { "IOU settlement flow must be initiated by the borrower." }

            // Step 3. Create a transaction builder.
            progressTracker.currentStep = BUILDING
            val notary = iouToSettle.state.notary
            val builder = TransactionType.General.Builder(notary)

            // Step 4. Check we have enough cash to settle the requested amount.
            val cashBalance = serviceHub.vaultService.cashBalances[amount.token] ?:
                    throw IllegalArgumentException("Borrower has no ${amount.token} to settle.")
            val amountLeftToSettle = iouToSettle.state.data.amount - iouToSettle.state.data.paid
            require(cashBalance >= amount) { "Borrower has only $cashBalance but needs $amount to settle." }
            require(amountLeftToSettle >= amount) { "Borrower tried to settle with $amount but only needs $amountLeftToSettle" }

            // Step 5. Get some cash from the vault and add a spend to our transaction builder.
            serviceHub.vaultService.generateSpend(builder, amount, counterparty)

            // Step 6. Add the IOU input state and settle command to the transaction builder.
            val settleCommand = Command(IOUContract.Commands.Settle(), listOf(counterparty.owningKey, me.owningKey))
            // Add the input IOU and IOU settle command.
            builder.addCommand(settleCommand)
            builder.addInputState(iouToSettle)

            // Step 7. Only add an output IOU state of the IOU has not been fully settled.
            val amountRemaining = amountLeftToSettle - amount
            if (amountRemaining > Amount(0, amount.token)) {
                val settledIOU: IOUState = iouToSettle.state.data.pay(amount)
                builder.addOutputState(settledIOU)
            }

            // Step 8. Verify and sign the transaction.
            builder.toWireTransaction().toLedgerTransaction(serviceHub).verify()
            progressTracker.currentStep = SIGNING
            val ptx = serviceHub.signInitialTransaction(builder)

            // Step 9. Get counterparty signature.
            progressTracker.currentStep = COLLECTING
            val stx = subFlow(CollectSignaturesFlow(ptx, COLLECTING.childProgressTracker()))

            // Step 10. Finalize the transaction.
            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(stx, FINALISING.childProgressTracker())).single()
        }
    }

    @InitiatedBy(IOUSettleFlow.Initiator::class)
    class Responder(val otherParty: Party) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val flow = object : SignTransactionFlow(otherParty) {
                @Suspendable
                override fun checkTransaction(stx: SignedTransaction) {
                    // TODO: Add some checking.
                }
            }

            val stx = subFlow(flow)

            return waitForLedgerCommit(stx.id)
        }
    }
}
