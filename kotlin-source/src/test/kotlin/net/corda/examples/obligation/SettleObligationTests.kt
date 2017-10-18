package net.corda.examples.obligation

import net.corda.core.contracts.withoutIssuer
import net.corda.core.flows.FlowException
import net.corda.finance.POUNDS
import net.corda.testing.chooseIdentity
import net.corda.testing.node.MockNetwork

class SettleObligationTests : ObligationTests() {

    // Helper for
    private fun getCashoutputByOwner(
            cashStates: List<net.corda.finance.contracts.asset.Cash.State>,
            node: net.corda.node.internal.StartedNode<MockNetwork.MockNode>
    ): net.corda.finance.contracts.asset.Cash.State {
        return cashStates.filter { cashState ->
            val cashOwner = node.services.identityService.requireWellKnownPartyFromAnonymous(cashState.owner)
            cashOwner == node.info.chooseIdentity()
        }.single()
    }

    @org.junit.Test
    fun `Settle flow can only be started by borrower`() {
        // Issue obligation.
        val issuanceTransaction = issueObligation(a, b, 1000.POUNDS, anonymous = false)
        net.waitQuiescent()
        val issuedObligation = issuanceTransaction.tx.outputStates.first() as net.corda.examples.obligation.Obligation

        // Attempt settlement.
        kotlin.test.assertFailsWith<FlowException> {
            settleObligation(issuedObligation.linearId, b, 1000.POUNDS)
        }
    }

    @org.junit.Test
    fun `Settle flow fails when borrower has no cash`() {
        // Issue obligation.
        val issuanceTransaction = issueObligation(a, b, 1000.POUNDS, anonymous = false)
        net.waitQuiescent()
        val issuedObligation = issuanceTransaction.tx.outputStates.first() as net.corda.examples.obligation.Obligation

        // Attempt settlement.
        kotlin.test.assertFailsWith<FlowException> {
            settleObligation(issuedObligation.linearId, a, 1000.POUNDS)
        }
    }

    @org.junit.Test
    fun `Settle flow fails when borrower pledges too much cash to settle`() {
        // Self issue cash.
        selfIssueCash(a, 1500.POUNDS)
        net.waitQuiescent()

        // Issue obligation.
        val issuanceTransaction = issueObligation(a, b, 1000.POUNDS, anonymous = false)
        net.waitQuiescent()
        val issuedObligation = issuanceTransaction.tx.outputStates.first() as net.corda.examples.obligation.Obligation

        // Attempt settlement.
        kotlin.test.assertFailsWith<FlowException> {
            settleObligation(issuedObligation.linearId, a, 1500.POUNDS)
        }
    }

    @org.junit.Test
    fun `Fully settle non-anonymous obligation`() {
        // Self issue cash.
        selfIssueCash(a, 1500.POUNDS)
        net.waitQuiescent()

        // Issue obligation.
        val issuanceTransaction = issueObligation(a, b, 1000.POUNDS, anonymous = false)
        net.waitQuiescent()
        val issuedObligation = issuanceTransaction.tx.outputStates.first() as net.corda.examples.obligation.Obligation

        // Attempt settlement.
        val settleTransaction = settleObligation(issuedObligation.linearId, a, 1000.POUNDS)
        net.waitQuiescent()
        assert(settleTransaction.tx.outputsOfType<net.corda.examples.obligation.Obligation>().isEmpty())

        // Check both parties have the transaction.
        val aTx = a.services.validatedTransactions.getTransaction(settleTransaction.id)
        val bTx = b.services.validatedTransactions.getTransaction(settleTransaction.id)
        kotlin.test.assertEquals(aTx, bTx)
    }

    @org.junit.Test
    fun `Fully settle anonymous obligation`() {
        // Self issue cash.
        selfIssueCash(a, 1500.POUNDS)

        // Issue obligation.
        val issuanceTransaction = issueObligation(a, b, 1000.POUNDS, anonymous = true)
        net.waitQuiescent()
        val issuedObligation = issuanceTransaction.tx.outputStates.first() as net.corda.examples.obligation.Obligation

        // Attempt settlement.
        val settleTransaction = settleObligation(issuedObligation.linearId, a, 1000.POUNDS)
        net.waitQuiescent()
        assert(settleTransaction.tx.outputsOfType<net.corda.examples.obligation.Obligation>().isEmpty())

        // Check both parties have the transaction.
        val aTx = a.services.validatedTransactions.getTransaction(settleTransaction.id)
        val bTx = b.services.validatedTransactions.getTransaction(settleTransaction.id)
        kotlin.test.assertEquals(aTx, bTx)
    }

    @org.junit.Test
    fun `Partially settle non-anonymous obligation with non-anonymous cash payment`() {
        // Self issue cash.
        selfIssueCash(a, 1500.POUNDS)
        net.waitQuiescent()

        // Issue obligation.
        val issuanceTransaction = issueObligation(a, b, 1000.POUNDS, anonymous = false)
        net.waitQuiescent()
        val issuedObligation = issuanceTransaction.tx.outputStates.first() as net.corda.examples.obligation.Obligation

        // Attempt settlement.
        val amountToSettle = 500.POUNDS
        val settleTransaction = settleObligation(issuedObligation.linearId, a, amountToSettle, anonymous = false)
        net.waitQuiescent()
        assert(settleTransaction.tx.outputsOfType<net.corda.examples.obligation.Obligation>().size == 1)

        // Check both parties have the transaction.
        val aTx = a.services.validatedTransactions.getTransaction(settleTransaction.id)
        val bTx = b.services.validatedTransactions.getTransaction(settleTransaction.id)
        kotlin.test.assertEquals(aTx, bTx)

        // Check the obligation paid amount is correctly updated.
        val partiallySettledObligation = settleTransaction.tx.outputsOfType<net.corda.examples.obligation.Obligation>().single()
        assert(partiallySettledObligation.paid == amountToSettle)

        // Check cash has gone to the correct parties.
        val outputCash = settleTransaction.tx.outputsOfType<net.corda.finance.contracts.asset.Cash.State>()
        assert(outputCash.size == 2)       // Cash to b and change to a.

        // Change addresses are always anonymous, I think.
        val change = getCashoutputByOwner(outputCash, a)
        assert(change.amount.withoutIssuer() == 1000.POUNDS)

        val payment = outputCash.filter { it.owner == b.info.chooseIdentity() }.single()
        assert(payment.amount.withoutIssuer() == 500.POUNDS)
    }

    @org.junit.Test
    fun `Partially settle non-anonymous obligation with anonymous cash payment`() {
        // Self issue cash.
        selfIssueCash(a, 1500.POUNDS)
        net.waitQuiescent()

        // Issue obligation.
        val issuanceTransaction = issueObligation(a, b, 1000.POUNDS, anonymous = false)
        net.waitQuiescent()
        val issuedObligation = issuanceTransaction.tx.outputStates.first() as net.corda.examples.obligation.Obligation

        // Attempt settlement.
        val amountToSettle = 500.POUNDS
        val settleTransaction = settleObligation(issuedObligation.linearId, a, amountToSettle, anonymous = true)
        net.waitQuiescent()
        assert(settleTransaction.tx.outputsOfType<net.corda.examples.obligation.Obligation>().size == 1)

        // Check both parties have the transaction.
        val aTx = a.services.validatedTransactions.getTransaction(settleTransaction.id)
        val bTx = b.services.validatedTransactions.getTransaction(settleTransaction.id)
        kotlin.test.assertEquals(aTx, bTx)

        // Check the obligation paid amount is correctly updated.
        val partiallySettledObligation = settleTransaction.tx.outputsOfType<net.corda.examples.obligation.Obligation>().single()
        assert(partiallySettledObligation.paid == amountToSettle)

        // Check cash has gone to the correct parties.
        val outputCash = settleTransaction.tx.outputsOfType<net.corda.finance.contracts.asset.Cash.State>()
        assert(outputCash.size == 2)       // Cash to b and change to a.

        val change = getCashoutputByOwner(outputCash, a)
        assert(change.amount.withoutIssuer() == 1000.POUNDS)

        val payment = getCashoutputByOwner(outputCash, b)
        assert(payment.amount.withoutIssuer() == 500.POUNDS)
    }

    @org.junit.Test
    fun `Partially settle anonymous obligation with anonymous cash payment`() {
        // Self issue cash.
        selfIssueCash(a, 1500.POUNDS)
        net.waitQuiescent()

        // Issue obligation.
        val issuanceTransaction = issueObligation(a, b, 1000.POUNDS, anonymous = true)
        net.waitQuiescent()
        val issuedObligation = issuanceTransaction.tx.outputStates.first() as net.corda.examples.obligation.Obligation

        // Attempt settlement.
        val amountToSettle = 500.POUNDS
        val settleTransaction = settleObligation(issuedObligation.linearId, a, amountToSettle, anonymous = true)
        net.waitQuiescent()
        assert(settleTransaction.tx.outputsOfType<net.corda.examples.obligation.Obligation>().size == 1)

        // Check both parties have the transaction.
        val aTx = a.services.validatedTransactions.getTransaction(settleTransaction.id)
        val bTx = b.services.validatedTransactions.getTransaction(settleTransaction.id)
        kotlin.test.assertEquals(aTx, bTx)

        // Check the obligation paid amount is correctly updated.
        val partiallySettledObligation = settleTransaction.tx.outputsOfType<net.corda.examples.obligation.Obligation>().single()
        assert(partiallySettledObligation.paid == amountToSettle)

        // Check cash has gone to the correct parties.
        val outputCash = settleTransaction.tx.outputsOfType<net.corda.finance.contracts.asset.Cash.State>()
        assert(outputCash.size == 2)       // Cash to b and change to a.

        val change = getCashoutputByOwner(outputCash, a)
        assert(change.amount.withoutIssuer() == 1000.POUNDS)

        val payment = getCashoutputByOwner(outputCash, b)
        assert(payment.amount.withoutIssuer() == 500.POUNDS)
    }

}
