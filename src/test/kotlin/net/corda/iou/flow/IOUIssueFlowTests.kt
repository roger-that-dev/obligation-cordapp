package net.corda.iou.flow

import net.corda.core.contracts.POUNDS
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Practical exercise instructions.
 * Uncomment the unit tests and use the hints + unit test body to complete the FLows such that the unit tests pass.
 */
class IOUIssueFlowTests {
    lateinit var net: MockNetwork
    lateinit var a: MockNetwork.MockNode
    lateinit var b: MockNetwork.MockNode

    @Before
    fun setup() {
        net = MockNetwork()
        val nodes = net.createSomeNodes(2)
        a = nodes.partyNodes[0]
        b = nodes.partyNodes[1]
        nodes.partyNodes.forEach {
            it.registerInitiatedFlow(IOUIssueFlow.Responder::class.java)
        }
        net.runNetwork()
    }

    @After
    fun tearDown() {
        net.stopNodes()
    }


    @Test
    fun flowReturnsCorrectlyFormedPartiallySignedTransaction() {
        val iou = IOUState(10.POUNDS, a.info.legalIdentity, b.info.legalIdentity)
        val flow = IOUIssueFlow.Initiator(iou, b.info.legalIdentity)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()
        // Return the unsigned(!) SignedTransaction object from the IOUIssueFlow.
        val ptx: SignedTransaction = future.getOrThrow()
        // Print the transaction for debugging purposes.
        println(ptx.tx)
        // Check the transaction is well formed...
        // No outputs, one input IOUState and a command with the right properties.
        assert(ptx.tx.inputs.isEmpty())
        assert(ptx.tx.outputs.single().data is IOUState)
        val command = ptx.tx.commands.single()
        assert(command.value == IOUContract.Commands.Issue())
        assert(command.signers.toSet() == iou.participants.map { it.owningKey }.toSet())
        ptx.verifySignatures(b.info.legalIdentity.owningKey, DUMMY_NOTARY.owningKey)
    }

    @Test
    fun flowReturnsVerifiedPartiallySignedTransaction() {
        // Check that a zero amount IOU fails.
        val zeroIou = IOUState(0.POUNDS, a.info.legalIdentity, b.info.legalIdentity)
        val futureOne = a.services.startFlow(IOUIssueFlow.Initiator(zeroIou, b.info.legalIdentity)).resultFuture
        net.runNetwork()
        assertFailsWith<TransactionVerificationException> { futureOne.getOrThrow() }
        // Check that an IOU with the same participants fails.
        val borrowerIsLenderIou = IOUState(10.POUNDS, a.info.legalIdentity, a.info.legalIdentity)
        val futureTwo = a.services.startFlow(IOUIssueFlow.Initiator(borrowerIsLenderIou, b.info.legalIdentity)).resultFuture
        net.runNetwork()
        assertFailsWith<TransactionVerificationException> { futureTwo.getOrThrow() }
        // Check a good IOU passes.
        val iou = IOUState(10.POUNDS, a.info.legalIdentity, b.info.legalIdentity)
        val futureThree = a.services.startFlow(IOUIssueFlow.Initiator(iou, b.info.legalIdentity)).resultFuture
        net.runNetwork()
        futureThree.getOrThrow()
    }

    @Test
    fun flowReturnsTransactionSignedByBothParties() {
        val iou = IOUState(10.POUNDS, a.info.legalIdentity, b.info.legalIdentity)
        val flow = IOUIssueFlow.Initiator(iou, b.info.legalIdentity)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()
        val stx = future.getOrThrow()
        stx.verifySignatures()
    }

    @Test
    fun flowRecordsTheSameTransactionInBothPartyVaults() {
        val iou = IOUState(10.POUNDS, a.info.legalIdentity, b.info.legalIdentity)
        val flow = IOUIssueFlow.Initiator(iou, b.info.legalIdentity)
        val future = a.services.startFlow(flow).resultFuture
        net.runNetwork()
        val stx = future.getOrThrow()
        println("Signed transaction hash: ${stx.id}")
        listOf(a, b).map {
            it.storage.validatedTransactions.getTransaction(stx.id)
        }.forEach {
            val txHash = (it as SignedTransaction).id
            println("$txHash == ${stx.id}")
            assertEquals(stx.id, txHash)
        }
    }
}
