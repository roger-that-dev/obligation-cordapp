package net.corda.examples.obligation

import net.corda.core.contracts.withoutIssuer
import net.corda.core.flows.FlowException
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import net.corda.testing.chooseIdentity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SettleObligationTests : ObligationTests() {

    @Test
    fun `Settle flow can only be started by borrower`() {
        // Issue obligation.
        val issuanceTransaction = issueObligation(a, b, 1000.POUNDS, anonymous = false)
        net.waitQuiescent()
        val issuedObligation = issuanceTransaction.tx.outputStates.first() as Obligation

        // Attempt settlement.
        assertFailsWith<FlowException> {
            settleObligation(issuedObligation.linearId, b, 1000.POUNDS)
        }
    }

    @Test
    fun `Settle flow fails when borrower has no cash`() {
        // Issue obligation.
        val issuanceTransaction = issueObligation(a, b, 1000.POUNDS, anonymous = false)
        net.waitQuiescent()
        val issuedObligation = issuanceTransaction.tx.outputStates.first() as Obligation

        // Attempt settlement.
        assertFailsWith<FlowException> {
            settleObligation(issuedObligation.linearId, a, 1000.POUNDS)
        }
    }

    @Test
    fun `Settle flow fails when borrower pledges too much cash to settle`() {
        // Self issue cash.
        selfIssueCash(a, 1500.POUNDS)

        // Issue obligation.
        val issuanceTransaction = issueObligation(a, b, 1000.POUNDS, anonymous = false)
        net.waitQuiescent()
        val issuedObligation = issuanceTransaction.tx.outputStates.first() as Obligation

        // Attempt settlement.
        assertFailsWith<FlowException> {
            settleObligation(issuedObligation.linearId, a, 1500.POUNDS)
        }
    }

    @Test
    fun `Fully settle non-anonymous obligation`() {
        // Self issue cash.
        selfIssueCash(a, 1500.POUNDS)

        // Issue obligation.
        val issuanceTransaction = issueObligation(a, b, 1000.POUNDS, anonymous = false)
        net.waitQuiescent()
        val issuedObligation = issuanceTransaction.tx.outputStates.first() as Obligation

        // Attempt settlement.
        val settleTransaction = settleObligation(issuedObligation.linearId, a, 1000.POUNDS)
        assert(settleTransaction.tx.outputsOfType<Obligation>().isEmpty())

        // Check both parties have the transaction.
        val aTx = a.services.validatedTransactions.getTransaction(settleTransaction.id)
        val bTx = b.services.validatedTransactions.getTransaction(settleTransaction.id)
        assertEquals(aTx, bTx)
    }

    @Test
    fun `Fully settle anonymous obligation`() {
        // Self issue cash.
        selfIssueCash(a, 1500.POUNDS)

        // Issue obligation.
        val issuanceTransaction = issueObligation(a, b, 1000.POUNDS, anonymous = true)
        net.waitQuiescent()
        val issuedObligation = issuanceTransaction.tx.outputStates.first() as Obligation

        // Attempt settlement.
        val settleTransaction = settleObligation(issuedObligation.linearId, a, 1000.POUNDS)
        assert(settleTransaction.tx.outputsOfType<Obligation>().isEmpty())

        // Check both parties have the transaction.
        val aTx = a.services.validatedTransactions.getTransaction(settleTransaction.id)
        val bTx = b.services.validatedTransactions.getTransaction(settleTransaction.id)
        assertEquals(aTx, bTx)
    }

    @Test
    fun `Partially settle non-anonymous obligation with non-anonymous cash payment`() {
        // Self issue cash.
        selfIssueCash(a, 1500.POUNDS)

        // Issue obligation.
        val issuanceTransaction = issueObligation(a, b, 1000.POUNDS, anonymous = false)
        net.waitQuiescent()
        val issuedObligation = issuanceTransaction.tx.outputStates.first() as Obligation

        // Attempt settlement.
        val amountToSettle = 500.POUNDS
        val settleTransaction = settleObligation(issuedObligation.linearId, a, amountToSettle, anonymous = false)
        assert(settleTransaction.tx.outputsOfType<Obligation>().size == 1)

        // Check both parties have the transaction.
        val aTx = a.services.validatedTransactions.getTransaction(settleTransaction.id)
        val bTx = b.services.validatedTransactions.getTransaction(settleTransaction.id)
        assertEquals(aTx, bTx)

        // Check the obligation paid amount is correctly updated.
        val partiallySettledObligation = settleTransaction.tx.outputsOfType<Obligation>().single()
        assert(partiallySettledObligation.paid == amountToSettle)

        // Check cash has gone to the correct parties.
        val outputCash = settleTransaction.tx.outputsOfType<Cash.State>()
        assert(outputCash.size == 2)       // Cash to b and change to a.

        // Change addresses are always anonymous, I think.
        val change = outputCash.filter { cashState ->
            val cashOwner = a.services.identityService.requireWellKnownPartyFromAnonymous(cashState.owner)
            cashOwner == a.info.chooseIdentity()
        }.single()
        assert(change.amount.withoutIssuer() == 1000.POUNDS)

        val payment = outputCash.filter { it.owner == b.info.chooseIdentity() }.single()
        assert(payment.amount.withoutIssuer() == 500.POUNDS)
    }

    @Test
    fun `Partially settle non-anonymous obligation with anonymous cash payment`() {
        // Self issue cash.
        selfIssueCash(a, 1500.POUNDS)

        // Issue obligation.
        val issuanceTransaction = issueObligation(a, b, 1000.POUNDS, anonymous = false)
        net.waitQuiescent()
        val issuedObligation = issuanceTransaction.tx.outputStates.first() as Obligation

        // Attempt settlement.
        val amountToSettle = 500.POUNDS
        val settleTransaction = settleObligation(issuedObligation.linearId, a, amountToSettle, anonymous = true)
        assert(settleTransaction.tx.outputsOfType<Obligation>().size == 1)

        // Check both parties have the transaction.
        val aTx = a.services.validatedTransactions.getTransaction(settleTransaction.id)
        val bTx = b.services.validatedTransactions.getTransaction(settleTransaction.id)
        assertEquals(aTx, bTx)

        // Check the obligation paid amount is correctly updated.
        val partiallySettledObligation = settleTransaction.tx.outputsOfType<Obligation>().single()
        assert(partiallySettledObligation.paid == amountToSettle)

        // Check cash has gone to the correct parties.
        val outputCash = settleTransaction.tx.outputsOfType<Cash.State>()
        assert(outputCash.size == 2)       // Cash to b and change to a.

        // Change addresses are always anonymous, I think.
        val change = outputCash.filter { cashState ->
            val cashOwner = a.services.identityService.requireWellKnownPartyFromAnonymous(cashState.owner)
            cashOwner == a.info.chooseIdentity()
        }.single()
        assert(change.amount.withoutIssuer() == 1000.POUNDS)

        val payment = outputCash.filter { cashState ->
            val cashOwner = b.services.identityService.requireWellKnownPartyFromAnonymous(cashState.owner)
            cashOwner == b.info.chooseIdentity()
        }.single()
        assert(payment.amount.withoutIssuer() == 500.POUNDS)
    }

    @Test
    fun `Partially settle anonymous obligation with anonymous cash payment`() {
        // Self issue cash.
        selfIssueCash(a, 1500.POUNDS)

        // Issue obligation.
        val issuanceTransaction = issueObligation(a, b, 1000.POUNDS, anonymous = true)
        net.waitQuiescent()
        val issuedObligation = issuanceTransaction.tx.outputStates.first() as Obligation

        // Attempt settlement.
        val amountToSettle = 500.POUNDS
        val settleTransaction = settleObligation(issuedObligation.linearId, a, amountToSettle, anonymous = true)
        assert(settleTransaction.tx.outputsOfType<Obligation>().size == 1)

        // Check both parties have the transaction.
        val aTx = a.services.validatedTransactions.getTransaction(settleTransaction.id)
        val bTx = b.services.validatedTransactions.getTransaction(settleTransaction.id)
        assertEquals(aTx, bTx)

        // Check the obligation paid amount is correctly updated.
        val partiallySettledObligation = settleTransaction.tx.outputsOfType<Obligation>().single()
        assert(partiallySettledObligation.paid == amountToSettle)

        // Check cash has gone to the correct parties.
        val outputCash = settleTransaction.tx.outputsOfType<Cash.State>()
        assert(outputCash.size == 2)       // Cash to b and change to a.

        // Change addresses are always anonymous, I think.
        val change = outputCash.filter { cashState ->
            val cashOwner = a.services.identityService.requireWellKnownPartyFromAnonymous(cashState.owner)
            cashOwner == a.info.chooseIdentity()
        }.single()
        assert(change.amount.withoutIssuer() == 1000.POUNDS)

        val payment = outputCash.filter { cashState ->
            val cashOwner = b.services.identityService.requireWellKnownPartyFromAnonymous(cashState.owner)
            cashOwner == b.info.chooseIdentity()
        }.single()
        assert(payment.amount.withoutIssuer() == 500.POUNDS)
    }

}
