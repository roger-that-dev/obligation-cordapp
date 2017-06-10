package net.corda.iou.flow

import net.corda.contracts.asset.Cash
import net.corda.contracts.asset.sumCash
import net.corda.core.contracts.Amount
import net.corda.core.contracts.POUNDS
import net.corda.core.contracts.requireSingleCommand
import net.corda.core.contracts.withoutIssuer
import net.corda.core.getOrThrow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.iou.contract.IOUContract
import net.corda.iou.state.IOUState
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IOUSettleFlowTests {
    lateinit var net: MockNetwork
    lateinit var a: MockNetwork.MockNode
    lateinit var b: MockNetwork.MockNode
    lateinit var c: MockNetwork.MockNode

    @Before
    fun setup() {
        net = MockNetwork()
        val nodes = net.createSomeNodes(3)
        a = nodes.partyNodes[0]
        b = nodes.partyNodes[1]
        c = nodes.partyNodes[2]
        nodes.partyNodes.forEach {
            it.registerInitiatedFlow(IOUIssueFlow.Responder::class.java)
            it.registerInitiatedFlow(IOUSettleFlow.Responder::class.java)

        }
        net.runNetwork()
    }

    @After
    fun tearDown() {
        net.stopNodes()
    }

    /**
     * Issue an IOU on the ledger, we need to do this before we can transfer one.
     */
    private fun issueIou(iou: IOUState): SignedTransaction {
        val flow = IOUIssueFlow.Initiator(iou, b.info.legalIdentity)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()
        return future.getOrThrow()
    }

    /**
     * Issue an some on-ledger cash to ourselves, we need to do this before we can Settle an IOU.
     */
    private fun issueCash(amount: Amount<Currency>): Cash.State {
        val flow = SelfIssueCashFlow(amount)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()
        return future.getOrThrow()
    }

    @Test
    fun flowReturnsCorrectlyFormedPartiallySignedTransaction() {
        val stx = issueIou(IOUState(10.POUNDS, b.info.legalIdentity, a.info.legalIdentity))
        issueCash(5.POUNDS)
        val inputIou = stx.tx.outputs.single().data as IOUState
        val flow = IOUSettleFlow.Initiator(inputIou.linearId, 5.POUNDS)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()
        val settleResult = future.getOrThrow()
        // Check the transaction is well formed...
        // One output IOUState, one input IOUState reference, input and output cash
        val ledgerTx = settleResult.toLedgerTransaction(a.services)
        assert(ledgerTx.inputs.size == 2)
        assert(ledgerTx.outputs.size == 2)
        val outputIou = ledgerTx.outputs.map { it.data }.filterIsInstance<IOUState>().single()
        assertEquals(
                outputIou,
                inputIou.pay(5.POUNDS))
        // Sum all the output cash. This is complicated as there may be multiple cash output states with not all of them
        // being assigned to the lender.
        val outputCashSum = ledgerTx.outputs
                .map { it.data }
                .filterIsInstance<Cash.State>()
                .filter { it.owner == b.info.legalIdentity }
                .sumCash()
                .withoutIssuer()
        // Compare the cash assigned to the lender with the amount claimed is being settled by the borrower.
        assertEquals(
                outputCashSum,
                (inputIou.amount - inputIou.paid - outputIou.paid))
        val command = ledgerTx.commands.requireSingleCommand<IOUContract.Commands>()
        assert(command.value == IOUContract.Commands.Settle())
        // Check the transaction has been signed by the borrower.
        settleResult.verifySignatures(b.info.legalIdentity.owningKey, DUMMY_NOTARY.owningKey)
    }

    @Test
    fun settleFlowCanOnlyBeRunByBorrower() {
        val stx = issueIou(IOUState(10.POUNDS, b.info.legalIdentity, a.info.legalIdentity))
        issueCash(5.POUNDS)
        val inputIou = stx.tx.outputs.single().data as IOUState
        val flow = IOUSettleFlow.Initiator(inputIou.linearId, 5.POUNDS)
        val future = b.services.startFlow(flow).resultFuture
        net.runNetwork()
        assertFailsWith<IllegalArgumentException> { future.getOrThrow() }
    }

    @Test
    fun borrowerMustHaveCashInRightCurrency() {
        val stx = issueIou(IOUState(10.POUNDS, b.info.legalIdentity, a.info.legalIdentity))
        val inputIou = stx.tx.outputs.single().data as IOUState
        val flow = IOUSettleFlow.Initiator(inputIou.linearId, 5.POUNDS)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()
        assertFailsWith<IllegalArgumentException>("Borrower has no GBP to settle.") { future.getOrThrow() }
    }

    @Test
    fun borrowerMustHaveEnoughCashInRightCurrency() {
        val stx = issueIou(IOUState(10.POUNDS, b.info.legalIdentity, a.info.legalIdentity))
        issueCash(1.POUNDS)
        val inputIou = stx.tx.outputs.single().data as IOUState
        val flow = IOUSettleFlow.Initiator(inputIou.linearId, 5.POUNDS)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()
        assertFailsWith<IllegalArgumentException>("Borrower has only 1.00 GBP but needs 5.00 GBP to settle.") { future.getOrThrow() }
    }

    @Test
    fun flowReturnsTransactionSignedByBothParties() {
        val stx = issueIou(IOUState(10.POUNDS, b.info.legalIdentity, a.info.legalIdentity))
        issueCash(5.POUNDS)
        val inputIou = stx.tx.outputs.single().data as IOUState
        val flow = IOUSettleFlow.Initiator(inputIou.linearId, 5.POUNDS)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()
        val settleResult = future.getOrThrow()
        // Check the transaction is well formed...
        // One output IOUState, one input IOUState reference, input and output cash
        settleResult.verifySignatures(DUMMY_NOTARY.owningKey)
    }

    @Test
    fun flowReturnsCommittedTransaction() {
        val stx = issueIou(IOUState(10.POUNDS, b.info.legalIdentity, a.info.legalIdentity))
        issueCash(5.POUNDS)
        val inputIou = stx.tx.outputs.single().data as IOUState
        val flow = IOUSettleFlow.Initiator(inputIou.linearId, 5.POUNDS)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()
        val settleResult = future.getOrThrow()
        // Check the transaction is well formed...
        // One output IOUState, one input IOUState reference, input and output cash
        settleResult.verifySignatures()
    }
}
