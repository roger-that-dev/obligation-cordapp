package net.corda.examples.obligation

import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.contracts.getCashBalances
import net.corda.finance.flows.CashIssueFlow
import java.util.*
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("obligation")
class ObligationApi(val services: CordaRPCOps) {

    private val myIdentity = services.nodeInfo().legalIdentities.first()

    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    fun me() = mapOf("me" to myIdentity)

    @GET
    @Path("peers")
    @Produces(MediaType.APPLICATION_JSON)
    fun peers(): Map<String, List<CordaX500Name>> {
        val networkMap = services.networkMapSnapshot()
        return mapOf("peers" to networkMap
                .filter { nodeInfo -> nodeInfo.legalIdentities.first() != myIdentity }
                .map { it.legalIdentities.first().name })
    }

    @GET
    @Path("owed-per-currency")
    @Produces(MediaType.APPLICATION_JSON)
    fun owedPerCurrency(): Map<Currency, Long> {
        return services.vaultQuery(Obligation::class.java).states
                .filter { (state) -> state.data.lender != myIdentity }
                .map { (state) -> state.data.amount }
                .groupBy({ amount -> amount.token }, { (quantity) -> quantity })
                .mapValues { it.value.sum() }
    }

    @GET
    @Path("obligations")
    @Produces(MediaType.APPLICATION_JSON)
    fun obligations(): List<StateAndRef<ContractState>> = services.vaultQuery(Obligation::class.java).states

    @GET
    @Path("cash")
    @Produces(MediaType.APPLICATION_JSON)
    fun cash(): List<StateAndRef<Cash.State>> = services.vaultQuery(Cash.State::class.java).states

    @GET
    @Path("cash-balances")
    @Produces(MediaType.APPLICATION_JSON)
    fun getCashBalances(): Map<Currency, Amount<Currency>> = services.getCashBalances()

    @GET
    @Path("self-issue-cash")
    fun selfIssueCash(@QueryParam(value = "amount") amount: Int,
                      @QueryParam(value = "currency") currency: String): Response {

        // 1. Prepare issue request.
        val issueAmount = Amount(amount.toLong() * 100, Currency.getInstance(currency))
        val notary = services.notaryIdentities().firstOrNull() ?: throw IllegalStateException("Could not find a notary.")
        val issueRef = OpaqueBytes.of(0)
        val issueRequest = CashIssueFlow.IssueRequest(issueAmount, issueRef, notary)

        // 2. Start flow and wait for response.
        val (status, message) = try {
            val flowHandle = services.startTrackedFlowDynamic(CashIssueFlow::class.java, issueRequest)
            val result = flowHandle.use { it.returnValue.getOrThrow() }
            Response.Status.CREATED to result.toString()
        } catch (e: Exception) {
            Response.Status.BAD_REQUEST to e.message
        }

        // 3. Return the response.
        return Response.status(status).entity(message).build()
    }

    @GET
    @Path("issue-obligation")
    fun issueIOU(@QueryParam(value = "amount") amount: Int,
                 @QueryParam(value = "currency") currency: String,
                 @QueryParam(value = "party") party: String): Response {
        // 1. Get party objects for the counterparty.
        val lenderIdentity = services.partiesFromName(party, exactMatch = false).singleOrNull()
                ?: throw IllegalStateException("Couldn't lookup node identity for $party.")

        // 2. Create an amount object.
        val issueAmount = Amount(amount.toLong() * 100, Currency.getInstance(currency))

        // 3. Start the IssueObligation flow. We block and wait for the flow to return.
        val (status, message) = try {
            val flowHandle = services.startTrackedFlowDynamic(
                    IssueObligation.Initiator::class.java,
                    issueAmount,
                    lenderIdentity,
                    true
            )

            val result = flowHandle.use { it.returnValue.getOrThrow() }
            Response.Status.CREATED to "Transaction id ${result.id} committed to ledger.\n${result.tx.outputs.single()}"
        } catch (e: Exception) {
            Response.Status.BAD_REQUEST to e.message
        }

        // 4. Return the result.
        return Response.status(status).entity(message).build()
    }

    @GET
    @Path("transfer-obligation")
    fun transferObligation(@QueryParam(value = "id") id: String,
                           @QueryParam(value = "party") party: String): Response {
        val linearId = UniqueIdentifier.fromString(id)
        val newLender = services.partiesFromName(party, exactMatch = false).singleOrNull()
                ?: throw IllegalStateException("Couldn't lookup node identity for $party.")

        val (status, message) = try {
            val flowHandle = services.startTrackedFlowDynamic(
                    TransferObligation.Initiator::class.java,
                    linearId,
                    newLender,
                    true
            )

            flowHandle.use { flowHandle.returnValue.getOrThrow() }
            Response.Status.CREATED to "Obligation $id transferred to $party."
        } catch (e: Exception) {
            Response.Status.BAD_REQUEST to e.message
        }

        return Response.status(status).entity(message).build()
    }

//    /**
//     * Settles an IOU. Requires cash in the right currency to be able to settle.
//     */
//    @GET
//    @Path("settle-obligation")
//    fun settleIOU(@QueryParam(value = "id") id: String,
//                  @QueryParam(value = "amount") amount: Int,
//                  @QueryParam(value = "currency") currency: String): Response {
//        val linearId = UniqueIdentifier.Companion.Companion.fromString(id)
//        val settleAmount = Amount(amount.toLong() * 100, Currency.getInstance(currency))
//
//        val (status, message) = try {
//            val flowHandle = services.startTrackedFlowDynamic(net.corda.iou.flow.IOUSettleFlow.Initiator::class.java, linearId, settleAmount)
//            flowHandle.use { flowHandle.returnValue.getOrThrow() }
//            Response.Status.CREATED to "$amount $currency paid off on IOU id $id."
//        } catch (e: Exception) {
//            Response.Status.BAD_REQUEST to e.message
//        }
//
//        return Response.status(status).entity(message).build()
//    }
//

}