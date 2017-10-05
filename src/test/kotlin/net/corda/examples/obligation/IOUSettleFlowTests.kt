//package net.corda.examples.obligation
//
//import net.corda.contracts.asset.Cash
//import net.corda.contracts.asset.sumCash
//import net.corda.core.contracts.POUNDS
//import net.corda.core.getOrThrow
//import net.corda.core.utilities.DUMMY_NOTARY
//import java.util.*
//
//class IOUSettleFlowTests {
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
//            it.registerInitiatedFlow(net.corda.iou.flow.IOUSettleFlow.Responder::class.java)
//
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
//    /**
//     * Issue an some on-ledger cash to ourselves, we need to do this before we can Settle an IOU.
//     */
//    private fun issueCash(amount: net.corda.core.contracts.Amount<Currency>): Cash.State {
//        val flow = net.corda.samples.iou.flow.SelfIssueCashFlow(amount)
//        val future = a.services.startFlow(flow).resultFuture
//        net.runNetwork()
//        return future.getOrThrow()
//    }
//
//    @org.junit.Test
//    fun flowReturnsCorrectlyFormedPartiallySignedTransaction() {
//        val stx = issueIou(net.corda.samples.iou.state.IOUState(10.POUNDS, b.info.legalIdentity, a.info.legalIdentity))
//        issueCash(5.POUNDS)
//        val inputIou = stx.tx.outputs.single().data as Obligation
//        val flow = net.corda.iou.flow.IOUSettleFlow.Initiator(inputIou.linearId, 5.POUNDS)
//        val future = a.services.startFlow(flow).resultFuture
//        net.runNetwork()
//        val settleResult = future.getOrThrow()
//        // Check the transaction is well formed...
//        // One output Obligation, one input Obligation reference, input and output cash
//        val ledgerTx = settleResult.toLedgerTransaction(a.services)
//        assert(ledgerTx.inputs.size == 2)
//        assert(ledgerTx.outputs.size == 2)
//        val outputIou = ledgerTx.outputs.map { it.data }.filterIsInstance<Obligation>().single()
//        kotlin.test.assertEquals(
//                outputIou,
//                inputIou.pay(5.POUNDS))
//        // Sum all the output cash. This is complicated as there may be multiple cash output states with not all of them
//        // being assigned to the lender.
//        val outputCashSum = ledgerTx.outputs
//                .map { it.data }
//                .filterIsInstance<Cash.State>()
//                .filter { it.owner == b.info.legalIdentity }
//                .sumCash()
//                .withoutIssuer()
//        // Compare the cash assigned to the lender with the amount claimed is being settled by the borrower.
//        kotlin.test.assertEquals(
//                outputCashSum,
//                (inputIou.amount - inputIou.paid - outputIou.paid))
//        val command = ledgerTx.commands.requireSingleCommand<net.corda.iou.contract.IOUContract.Commands>()
//        assert(command.value == net.corda.iou.contract.IOUContract.Commands.Settle())
//        // Check the transaction has been signed by the borrower.
//        settleResult.verifySignatures(b.info.legalIdentity.owningKey, DUMMY_NOTARY.owningKey)
//    }
//
//    @org.junit.Test
//    fun settleFlowCanOnlyBeRunByBorrower() {
//        val stx = issueIou(net.corda.samples.iou.state.IOUState(10.POUNDS, b.info.legalIdentity, a.info.legalIdentity))
//        issueCash(5.POUNDS)
//        val inputIou = stx.tx.outputs.single().data as Obligation
//        val flow = net.corda.iou.flow.IOUSettleFlow.Initiator(inputIou.linearId, 5.POUNDS)
//        val future = b.services.startFlow(flow).resultFuture
//        net.runNetwork()
//        kotlin.test.assertFailsWith<IllegalArgumentException> { future.getOrThrow() }
//    }
//
//    @org.junit.Test
//    fun borrowerMustHaveCashInRightCurrency() {
//        val stx = issueIou(net.corda.samples.iou.state.IOUState(10.POUNDS, b.info.legalIdentity, a.info.legalIdentity))
//        val inputIou = stx.tx.outputs.single().data as Obligation
//        val flow = net.corda.iou.flow.IOUSettleFlow.Initiator(inputIou.linearId, 5.POUNDS)
//        val future = a.services.startFlow(flow).resultFuture
//        net.runNetwork()
//        kotlin.test.assertFailsWith<IllegalArgumentException>("Borrower has no GBP to settle.") { future.getOrThrow() }
//    }
//
//    @org.junit.Test
//    fun borrowerMustHaveEnoughCashInRightCurrency() {
//        val stx = issueIou(net.corda.samples.iou.state.IOUState(10.POUNDS, b.info.legalIdentity, a.info.legalIdentity))
//        issueCash(1.POUNDS)
//        val inputIou = stx.tx.outputs.single().data as Obligation
//        val flow = net.corda.iou.flow.IOUSettleFlow.Initiator(inputIou.linearId, 5.POUNDS)
//        val future = a.services.startFlow(flow).resultFuture
//        net.runNetwork()
//        kotlin.test.assertFailsWith<IllegalArgumentException>("Borrower has only 1.00 GBP but needs 5.00 GBP to settle.") { future.getOrThrow() }
//    }
//
//    @org.junit.Test
//    fun flowReturnsTransactionSignedByBothParties() {
//        val stx = issueIou(net.corda.samples.iou.state.IOUState(10.POUNDS, b.info.legalIdentity, a.info.legalIdentity))
//        issueCash(5.POUNDS)
//        val inputIou = stx.tx.outputs.single().data as Obligation
//        val flow = net.corda.iou.flow.IOUSettleFlow.Initiator(inputIou.linearId, 5.POUNDS)
//        val future = a.services.startFlow(flow).resultFuture
//        net.runNetwork()
//        val settleResult = future.getOrThrow()
//        // Check the transaction is well formed...
//        // One output Obligation, one input Obligation reference, input and output cash
//        settleResult.verifySignatures(DUMMY_NOTARY.owningKey)
//    }
//
//    @org.junit.Test
//    fun flowReturnsCommittedTransaction() {
//        val stx = issueIou(net.corda.samples.iou.state.IOUState(10.POUNDS, b.info.legalIdentity, a.info.legalIdentity))
//        issueCash(5.POUNDS)
//        val inputIou = stx.tx.outputs.single().data as Obligation
//        val flow = net.corda.iou.flow.IOUSettleFlow.Initiator(inputIou.linearId, 5.POUNDS)
//        val future = a.services.startFlow(flow).resultFuture
//        net.runNetwork()
//        val settleResult = future.getOrThrow()
//        // Check the transaction is well formed...
//        // One output Obligation, one input Obligation reference, input and output cash
//        settleResult.verifySignatures()
//    }
//}
