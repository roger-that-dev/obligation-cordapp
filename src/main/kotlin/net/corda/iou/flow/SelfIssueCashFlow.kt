package net.corda.iou.flow

import co.paralleluniverse.fibers.Suspendable
import net.corda.contracts.asset.Cash
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.utilities.ProgressTracker
import net.corda.flows.CashIssueFlow
import java.util.*

/**
 * Self issues the calling node an amount of cash in the desired currency.
 * Only used for demo/sample/iou purposes!
 */
@StartableByRPC
@InitiatingFlow
class SelfIssueCashFlow(val amount: Amount<Currency>) : FlowLogic<Cash.State>() {

    override val progressTracker: ProgressTracker = SelfIssueCashFlow.tracker()

    companion object {
        object PREPARING : ProgressTracker.Step("Preparing to self issue cash.")
        object ISSUING : ProgressTracker.Step("Issuing cash")

        fun tracker() = ProgressTracker(PREPARING, ISSUING)
    }

    @Suspendable
    override fun call(): Cash.State {
        /** Create the cash issue command. */
        val issueRef = OpaqueBytes.of(0)
        val notary = serviceHub.networkMapCache.notaryNodes.single().notaryIdentity
        val me = serviceHub.myInfo.legalIdentity
        /** Create the cash issuance transaction. */
        progressTracker.currentStep = PREPARING
        val cashIssueTransaction = subFlow(CashIssueFlow(amount, issueRef, me, notary))
        /** Return the cash output. */
        return cashIssueTransaction.tx.outputs.single().data as Cash.State
    }
}
