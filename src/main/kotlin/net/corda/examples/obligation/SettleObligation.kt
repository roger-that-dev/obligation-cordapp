package net.corda.examples.obligation

import co.paralleluniverse.fibers.Suspendable
import net.corda.confidential.IdentitySyncFlow
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.core.contracts.Amount
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
import net.corda.examples.obligation.ObligationContract.Companion.OBLIGATION_CONTRACT_ID
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.getCashBalance
import java.util.*

object SettleObligation {
    @InitiatingFlow
    @StartableByRPC
    class Initiator(val linearId: UniqueIdentifier,
                    val amount: Amount<Currency>,
                    val anonymous: Boolean = true
    ) : FlowLogic<SignedTransaction>() {

        override val progressTracker: ProgressTracker = tracker()

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
            // Stage 1. Retrieve obligation specified by linearId from the vault.
            progressTracker.currentStep = PREPARATION
            val queryCriteria = QueryCriteria.LinearStateQueryCriteria(uuid = listOf(linearId.id))
            val obligationToSettle = serviceHub.vaultService.queryBy<Obligation>(queryCriteria).states.single()
            val inputObligation = obligationToSettle.state.data

            // Stage 2. This flow can only be initiated by the current recipient.
            val borrowerIdentity = if (inputObligation.borrower is AnonymousParty) {
                serviceHub.identityService.requireWellKnownPartyFromAnonymous(inputObligation.borrower)
            } else {
                inputObligation.borrower
            }

            // Stage 3. Resolve identity if the obligation is anonymous.
            val lenderIdentity = if (inputObligation.lender is AnonymousParty) {
                serviceHub.identityService.requireWellKnownPartyFromAnonymous(inputObligation.lender)
            } else {
                inputObligation.lender as Party
            }

            check(borrowerIdentity == ourIdentity) {
                throw FlowException("Settle Obligation flow must be initiated by the borrower.")
            }

            // Stage 4. Check we have enough cash to settle the requested amount.
            val cashBalance = serviceHub.getCashBalance(amount.token)
            check(cashBalance.quantity > 0L) {
                throw FlowException("Borrower has no ${amount.token} to settle.")
            }

            val amountLeftToSettle = inputObligation.amount - inputObligation.paid
            check(cashBalance >= amount) {
                throw FlowException("Borrower has only $cashBalance but needs $amount to settle.")
            }

            check(amountLeftToSettle >= amount) {
                throw FlowException("There's only $amountLeftToSettle but you pledged $amount.")
            }

            // Stage 5. Create a settle command.
            val settleCommand = Command(
                    ObligationContract.Commands.Settle(),
                    inputObligation.participants.map { it.owningKey }
            )

            // Stage 6. Create a transaction builder. Add the settle command and input obligation.
            progressTracker.currentStep = BUILDING
            val notary = serviceHub.networkMapCache.notaryIdentities.firstOrNull()
                    ?: throw IllegalStateException("No available notaries.")
            val builder = TransactionBuilder(notary = notary)
                    .addInputState(obligationToSettle)
                    .addCommand(settleCommand)

            // Stage 7. Get some cash from the vault and add a spend to our transaction builder.
            val lenderPaymentKey = if (anonymous) {
                // TODO: Is there a flow to get a key and cert only from the counterparty?
                val txKeys = subFlow(SwapIdentitiesFlow(lenderIdentity))
                txKeys[lenderIdentity] ?: throw FlowException("Couldn't get lender's conf. identity.")
            } else {
                lenderIdentity
            }

            val (_, cashKeys) = Cash.generateSpend(serviceHub, builder, amount, lenderPaymentKey)

            // Stage 8. Only add an output obligation state if the obligation has not been fully settled.
            val amountRemaining = amountLeftToSettle - amount
            if (amountRemaining > Amount.zero(amount.token)) {
                val outputObligation = inputObligation.pay(amount)
                builder.addOutputState(outputObligation, OBLIGATION_CONTRACT_ID)
            }

            // Stage 9. Verify and sign the transaction.
            progressTracker.currentStep = SIGNING
            builder.verify(serviceHub)
            val ptx = serviceHub.signInitialTransaction(builder, cashKeys + inputObligation.borrower.owningKey)

            // Stage 10. Get counterparty signature.
            progressTracker.currentStep = COLLECTING
            val session = initiateFlow(lenderIdentity)
            subFlow(IdentitySyncFlow.Send(session, ptx.tx))
            val stx = subFlow(CollectSignaturesFlow(
                    ptx,
                    setOf(session),
                    cashKeys + inputObligation.borrower.owningKey,
                    COLLECTING.childProgressTracker())
            )

            // Stage 11. Finalize the transaction.
            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(stx, FINALISING.childProgressTracker()))
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
