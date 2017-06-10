package net.corda.iou.flow

import net.corda.core.contracts.POUNDS
import net.corda.core.contracts.StateRef
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.getOrThrow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.DUMMY_NOTARY
import net.corda.iou.contract.IOUContract
import net.corda.iou.state.IOUState
import net.corda.testing.node.MockNetwork
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertFailsWith

class IOUTransferFlowTests {
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
            it.registerInitiatedFlow(IOUTransferFlow.Responder::class.java)
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

    @Test
    fun flowReturnsCorrectlyFormedPartiallySignedTransaction() {
        val stx = issueIou(IOUState(10.POUNDS, a.info.legalIdentity, b.info.legalIdentity))
        val inputIou = stx.tx.outputs.single().data as IOUState
        val flow = IOUTransferFlow.Initiator(inputIou.linearId, c.info.legalIdentity)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()
        val ptx = future.getOrThrow()
        // Check the transaction is well formed...
        // One output IOUState, one input state reference and a Transfer command with the right properties.
        assert(ptx.tx.inputs.size == 1)
        assert(ptx.tx.outputs.size == 1)
        assert(ptx.tx.inputs.single() == StateRef(stx.id, 0))
        println("Input state ref: ${ptx.tx.inputs.single()} == ${StateRef(stx.id, 0)}")
        val outputIou = ptx.tx.outputs.single().data as IOUState
        println("Output state: $outputIou")
        val command = ptx.tx.commands.single()
        assert(command.value == IOUContract.Commands.Transfer())
        ptx.verifySignatures(b.info.legalIdentity.owningKey, c.info.legalIdentity.owningKey, DUMMY_NOTARY.owningKey)
    }

    @Test
    fun flowCanOnlyBeRunByCurrentLender() {
        val stx = issueIou(IOUState(10.POUNDS, a.info.legalIdentity, b.info.legalIdentity))
        val inputIou = stx.tx.outputs.single().data as IOUState
        val flow = IOUTransferFlow.Initiator(inputIou.linearId, c.info.legalIdentity)
        val future = b.services.startFlow(flow).resultFuture
        net.runNetwork()
        assertFailsWith<IllegalArgumentException> { future.getOrThrow() }
    }

    @Test
    fun iouCannotBeTransferredToSameParty() {
        val stx = issueIou(IOUState(10.POUNDS, a.info.legalIdentity, b.info.legalIdentity))
        val inputIou = stx.tx.outputs.single().data as IOUState
        val flow = IOUTransferFlow.Initiator(inputIou.linearId, a.info.legalIdentity)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()
        // Check that we can't transfer an IOU to ourselves.
        assertFailsWith<TransactionVerificationException> { future.getOrThrow() }
    }

    @Test
    fun flowReturnsTransactionSignedByAllParties() {
        val stx = issueIou(IOUState(10.POUNDS, a.info.legalIdentity, b.info.legalIdentity))
        val inputIou = stx.tx.outputs.single().data as IOUState
        val flow = IOUTransferFlow.Initiator(inputIou.linearId, c.info.legalIdentity)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()
        future.getOrThrow().verifySignatures(DUMMY_NOTARY.owningKey)
    }

    @Test
    fun flowReturnsTransactionSignedByAllPartiesAndNotary() {
        val stx = issueIou(IOUState(10.POUNDS, a.info.legalIdentity, b.info.legalIdentity))
        val inputIou = stx.tx.outputs.single().data as IOUState
        val flow = IOUTransferFlow.Initiator(inputIou.linearId, c.info.legalIdentity)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()
        future.getOrThrow().verifySignatures()
    }
}
