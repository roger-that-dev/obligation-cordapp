package net.corda.iou.flow

import co.paralleluniverse.fibers.Suspendable
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

/**
 * This is the flow which handles transfers of existing IOUs on the ledger.
 * This flow doesion't come in an Initiator and Responder pair as messaging across the network is handled by a [subFlow]
 * call to [CollectSignatureFlow.Initiator].
 * Notarisation (if required) and commitment to the ledger is handled vy the [FinalityFlow].
 * The flow returns the [SignedTransaction] that was committed to the ledger.
 */
object IOUTransferFlow {
    @StartableByRPC
    @InitiatingFlow
    class Initiator(val linearId: UniqueIdentifier, val newLender: Party) : FlowLogic<SignedTransaction>() {

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
            // Stage 1. Retrieve IOU specified by linearId from the vault.
            progressTracker.currentStep = PREPARATION
            val iouStates = serviceHub.vaultService.linearHeadsOfType<IOUState>()
            val iouStateAndRef = iouStates[linearId] ?: throw IllegalArgumentException("IOUState with linearId $linearId not found.")
            val inputIou = iouStateAndRef.state.data

            // Stage 2. This flow can only be initiated by the current recipient.
            require(serviceHub.myInfo.legalIdentity == inputIou.lender) { "IOU transfer can only be initiated by the IOU lender." }

            // Stage 3. Create the new IOU state reflecting a new lender.
            progressTracker.currentStep = BUILDING
            val outputIou = inputIou.withNewLender(newLender)

            // Stage 4. Create the transfer command.
            val signers = (inputIou.participants + newLender).map { it.owningKey }
            val transferCommand = Command(IOUContract.Commands.Transfer(), signers)

            // Stage 5. Get a reference to a transaction builder.
            val notary = serviceHub.networkMapCache.notaryNodes.single().notaryIdentity
            val builder = TransactionType.General.Builder(notary)

            // Stage 6. Create the transaction which comprises: one input, one output and one command.
            builder.withItems(iouStateAndRef, outputIou, transferCommand)

            // Stage 7. Verify and sign the transaction.
            builder.toWireTransaction().toLedgerTransaction(serviceHub).verify()
            progressTracker.currentStep = SIGNING
            val ptx = serviceHub.signInitialTransaction(builder)

            // Stage 8. Collect signature from borrower and the new lender and add it to the transaction.
            // This also verifies the transaction and checks the signatures.
            progressTracker.currentStep = COLLECTING
            val stx = subFlow(CollectSignaturesFlow(ptx, COLLECTING.childProgressTracker()))

            // Stage 9. Notarise and record, the transaction in our vaults.
            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(stx, setOf(inputIou.lender, inputIou.borrower, newLender))).single()
        }
    }

    @InitiatedBy(IOUTransferFlow.Initiator::class)
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