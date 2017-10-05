//package net.corda.examples.obligation
//
//import net.corda.core.contracts.POUNDS
//import net.corda.core.contracts.TransactionVerificationException
//import net.corda.core.getOrThrow
//import net.corda.core.utilities.DUMMY_NOTARY
//
//class IOUTransferFlowTests {
//    lateinit var net: net.corda.testing.node.MockNetwork
//    lateinit var a: net.corda.testing.node.MockNetwork.MockNode
//    lateinit var b: net.corda.testing.node.MockNetwork.MockNode
//    lateinit var c: net.corda.testing.node.MockNetwork.MockNode
//
//    @org.junit.Before
//    fun setup() {
//        net = net.corda.testing.node.MockNetwork()
//        val nodes = net.createSomeNodes(3)
//        a = nodes.partyNodes[0]
//        b = nodes.partyNodes[1]
//        c = nodes.partyNodes[2]
//        nodes.partyNodes.forEach {
//            it.registerInitiatedFlow(net.corda.iou.flow.IOUIssueFlow.Responder::class.java)
//            it.registerInitiatedFlow(net.corda.iou.flow.IOUTransferFlow.Responder::class.java)
//        }
//        net.runNetwork()
//    }
//
//    @org.junit.After
//    fun tearDown() {
//        net.stopNodes()
//    }
//
//    /**
//     * Issue an IOU on the ledger, we need to do this before we can transfer one.
//     */
//    private fun issueIou(iou: Obligation): net.corda.core.transactions.SignedTransaction {
//        val flow = net.corda.iou.flow.IOUIssueFlow.Initiator(iou, b.info.legalIdentity)
//        val future = a.services.startFlow(flow).resultFuture
//        net.runNetwork()
//        return future.getOrThrow()
//    }
//
//    @org.junit.Test
//    fun flowReturnsCorrectlyFormedPartiallySignedTransaction() {
//        val stx = issueIou(net.corda.samples.iou.state.IOUState(10.POUNDS, a.info.legalIdentity, b.info.legalIdentity))
//        val inputIou = stx.tx.outputs.single().data as Obligation
//        val flow = net.corda.iou.flow.IOUTransferFlow.Initiator(inputIou.linearId, c.info.legalIdentity)
//        val future = a.services.startFlow(flow).resultFuture
//        net.runNetwork()
//        val ptx = future.getOrThrow()
//        // Check the transaction is well formed...
//        // One output Obligation, one input state reference and a Transfer command with the right properties.
//        assert(ptx.tx.inputs.size == 1)
//        assert(ptx.tx.outputs.size == 1)
//        assert(ptx.tx.inputs.single() == net.corda.core.contracts.StateRef(stx.id, 0))
//        println("Input state ref: ${ptx.tx.inputs.single()} == ${net.corda.core.contracts.StateRef(stx.id, 0)}")
//        val outputIou = ptx.tx.outputs.single().data as Obligation
//        println("Output state: $outputIou")
//        val command = ptx.tx.commands.single()
//        assert(command.value == net.corda.iou.contract.IOUContract.Commands.Transfer())
//        ptx.verifySignatures(b.info.legalIdentity.owningKey, c.info.legalIdentity.owningKey, DUMMY_NOTARY.owningKey)
//    }
//
//    @org.junit.Test
//    fun flowCanOnlyBeRunByCurrentLender() {
//        val stx = issueIou(net.corda.samples.iou.state.IOUState(10.POUNDS, a.info.legalIdentity, b.info.legalIdentity))
//        val inputIou = stx.tx.outputs.single().data as Obligation
//        val flow = net.corda.iou.flow.IOUTransferFlow.Initiator(inputIou.linearId, c.info.legalIdentity)
//        val future = b.services.startFlow(flow).resultFuture
//        net.runNetwork()
//        kotlin.test.assertFailsWith<IllegalArgumentException> { future.getOrThrow() }
//    }
//
//    @org.junit.Test
//    fun iouCannotBeTransferredToSameParty() {
//        val stx = issueIou(net.corda.samples.iou.state.IOUState(10.POUNDS, a.info.legalIdentity, b.info.legalIdentity))
//        val inputIou = stx.tx.outputs.single().data as Obligation
//        val flow = net.corda.iou.flow.IOUTransferFlow.Initiator(inputIou.linearId, a.info.legalIdentity)
//        val future = a.services.startFlow(flow).resultFuture
//        net.runNetwork()
//        // Check that we can't transfer an IOU to ourselves.
//        kotlin.test.assertFailsWith<TransactionVerificationException> { future.getOrThrow() }
//    }
//
//    @org.junit.Test
//    fun flowReturnsTransactionSignedByAllParties() {
//        val stx = issueIou(net.corda.samples.iou.state.IOUState(10.POUNDS, a.info.legalIdentity, b.info.legalIdentity))
//        val inputIou = stx.tx.outputs.single().data as Obligation
//        val flow = net.corda.iou.flow.IOUTransferFlow.Initiator(inputIou.linearId, c.info.legalIdentity)
//        val future = a.services.startFlow(flow).resultFuture
//        net.runNetwork()
//        future.getOrThrow().verifySignatures(DUMMY_NOTARY.owningKey)
//    }
//
//    @org.junit.Test
//    fun flowReturnsTransactionSignedByAllPartiesAndNotary() {
//        val stx = issueIou(net.corda.samples.iou.state.IOUState(10.POUNDS, a.info.legalIdentity, b.info.legalIdentity))
//        val inputIou = stx.tx.outputs.single().data as Obligation
//        val flow = net.corda.iou.flow.IOUTransferFlow.Initiator(inputIou.linearId, c.info.legalIdentity)
//        val future = a.services.startFlow(flow).resultFuture
//        net.runNetwork()
//        future.getOrThrow().verifySignatures()
//    }
//}
