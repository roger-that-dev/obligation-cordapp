package net.corda.examples.obligation

import net.corda.core.flows.FlowException
import net.corda.finance.POUNDS
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

}
