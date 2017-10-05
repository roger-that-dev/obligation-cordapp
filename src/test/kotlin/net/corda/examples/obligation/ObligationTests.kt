package net.corda.examples.obligation

import net.corda.core.contracts.Amount
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.StartedNode
import net.corda.testing.chooseIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.setCordappPackages
import net.corda.testing.unsetCordappPackages
import org.junit.After
import org.junit.Before
import java.util.*

open class ObligationTests {
    lateinit var net: MockNetwork
    lateinit var a: StartedNode<MockNetwork.MockNode>
    lateinit var b: StartedNode<MockNetwork.MockNode>

    @Before
    fun setup() {
        setCordappPackages("net.corda.examples.obligation", "net.corda.finance")

        net = MockNetwork(threadPerNode = true)

        val nodes = net.createSomeNodes(2)
        a = nodes.partyNodes[0]
        b = nodes.partyNodes[1]

        nodes.partyNodes.forEach {
            it.registerInitiatedFlow(IssueObligation.Responder::class.java)
        }
    }

    @After
    fun tearDown() {
        net.stopNodes()
        unsetCordappPackages()
    }

    protected fun issueObligation(borrower: StartedNode<MockNetwork.MockNode>,
                                  lender: StartedNode<MockNetwork.MockNode>,
                                  amount: Amount<Currency>,
                                  anonymous: Boolean = true): SignedTransaction {
        val lenderIdentity = lender.info.chooseIdentity()
        val flow = IssueObligation.Initiator(amount, lenderIdentity, anonymous)
        return borrower.services.startFlow(flow).resultFuture.getOrThrow()
    }
}
