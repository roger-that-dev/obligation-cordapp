package net.corda.examples.obligation

import co.paralleluniverse.fibers.Suspendable
import net.corda.confidential.IdentitySyncFlow
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.contracts.asset.OBLIGATION_PROGRAM_ID

object TransferObligation {

    @StartableByRPC
    @InitiatingFlow
    class Initiator(val linearId: UniqueIdentifier,
                    val newLender: Party,
                    val anonymous: Boolean = true
    ) : FlowLogic<SignedTransaction>() {

        override val progressTracker: ProgressTracker = tracker()

        companion object {
            object PREPARATION : ProgressTracker.Step("Obtaining IOU from vault")
            object BUILDING : ProgressTracker.Step("Building and verifying transaction.")
            object SIGNING : ProgressTracker.Step("signing transaction.")

            object SYNCING : ProgressTracker.Step("Syncing identities.") {
                override fun childProgressTracker() = IdentitySyncFlow.Send.tracker()
            }

            object COLLECTING : ProgressTracker.Step("Collecting counterparty signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING : ProgressTracker.Step("Finalising transaction") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(PREPARATION, BUILDING, SIGNING, SYNCING, COLLECTING, FINALISING)
        }

        @Suspendable
        override fun call(): SignedTransaction {
            // Stage 1. Retrieve obligation specified by linearId from the vault.
            progressTracker.currentStep = PREPARATION
            val queryCriteria = QueryCriteria.LinearStateQueryCriteria(uuid = listOf(linearId.id))
            val obligationToTransfer = serviceHub.vaultService.queryBy<Obligation>(queryCriteria).states.singleOrNull()
                    ?: throw FlowException("Obligation with id $linearId not found.")
            val inputObligation = obligationToTransfer.state.data

            // Stage 2. This flow can only be initiated by the current recipient.
            val lenderIdentity = if (inputObligation.lender is AnonymousParty) {
                serviceHub.identityService.requireWellKnownPartyFromAnonymous(inputObligation.lender)
            } else {
                inputObligation.lender
            }

            // Stage 3. Abort if the borrower started this flow.
            check(ourIdentity == lenderIdentity) { "Obligation transfer can only be initiated by the lender." }

            // Stage 4. Create the new obligation state reflecting a new lender.
            progressTracker.currentStep = BUILDING
            val transferredObligation = if (anonymous) {
                // TODO: Is there a flow to get a key and cert only from the counterparty?
                val txKeys = subFlow(SwapIdentitiesFlow(newLender))
                val anonymousLender = txKeys[newLender] ?: throw FlowException("Couldn't get lender's conf. identity.")
                inputObligation.withNewLender(anonymousLender)
            } else {
                inputObligation.withNewLender(newLender)
            }

            // Stage 4. Create the transfer command.
            val signers = inputObligation.participants + transferredObligation.lender
            val signerKeys = signers.map { it.owningKey }
            val transferCommand = Command(ObligationContract.Commands.Transfer(), signerKeys)

            // Stage 5. Get a reference to a notary
            val notary = serviceHub.networkMapCache.notaryIdentities.firstOrNull()
                    ?: throw IllegalStateException("No available notaries.")

            // Stage 6. Create a transaction builder, then add the states and commands.
            val builder = TransactionBuilder(notary = notary)
                    .addInputState(obligationToTransfer)
                    .addOutputState(transferredObligation, OBLIGATION_PROGRAM_ID)
                    .addCommand(transferCommand)

            // Stage 7. Verify and sign the transaction.
            progressTracker.currentStep = SIGNING
            builder.verify(serviceHub)
            val ptx = serviceHub.signInitialTransaction(builder, inputObligation.lender.owningKey)

            // Stage 8. Get a Party object for the borrower.
            progressTracker.currentStep = SYNCING
            val borrower = if (inputObligation.borrower is AnonymousParty) {
                serviceHub.identityService.requireWellKnownPartyFromAnonymous(inputObligation.borrower)
            } else {
                inputObligation.borrower as Party
            }

            // Stage 9. Send any keys and certificates so the signers can verify each other's identity.
            val counterparties = listOf(borrower, newLender)
            val sessions = counterparties.map { party: Party -> initiateFlow(party) }.toSet()
            subFlow(IdentitySyncFlow.Send(sessions, ptx.tx, SYNCING.childProgressTracker()))

            // Stage 10. Collect signatures from the borrower and the new lender.
            progressTracker.currentStep = COLLECTING
            val stx = subFlow(CollectSignaturesFlow(
                    partiallySignedTx = ptx,
                    sessionsToCollectFrom = sessions,
                    myOptionalKeys = listOf(inputObligation.lender.owningKey),
                    progressTracker = COLLECTING.childProgressTracker())
            )

            // Stage 11. Notarise and record, the transaction in our vaults. Send a copy to me as well.
            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(stx, setOf(ourIdentity)))
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val otherFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            subFlow(IdentitySyncFlow.Receive(otherFlow))
            val flow = object : SignTransactionFlow(otherFlow) {
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