package net.corda.examples.obligation

import net.corda.core.messaging.CordaRPCOps
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType


/**
 * This API is accessible from /api/obligation. The endpoint paths specified below are relative to it.
 * We've defined a bunch of endpoints to deal with IOUs, cash and the various operations you can perform with them.
 */
@Path("obligation")
class ObligationApi(val services: CordaRPCOps) {
    private val myLegalName = services.nodeInfo().legalIdentities.first()

    /**
     * Returns the node's name.
     */
    @GET
    @Path("me")
    @Produces(MediaType.APPLICATION_JSON)
    fun whoami() = mapOf("me" to myLegalName)

    /**
     * Returns all parties registered with the [NetworkMapService]. These names can be used to look up identities
     * using the [IdentityService].
     */
//    @GET
//    @Path("peers")
//    @Produces(MediaType.APPLICATION_JSON)
//    fun getPeers(): Map<String, List<X500Name>> {
//        val (nodeInfo, nodeUpdates) = services.networkMapUpdates()
//        nodeUpdates.notUsed()
//        return mapOf("peers" to nodeInfo
//                .map { it.legalIdentity.name }
//                .filter { it != myLegalName && it !in SERVICE_NODE_NAMES })
//    }
//
//    /**
//     * Displays all IOU states that exist in the node's vault.
//     */
//    @GET
//    @Path("ious")
//    @Produces(MediaType.APPLICATION_JSON)
//    // Filter by state type: IOU.
//    fun getIOUs(): List<StateAndRef<ContractState>> {
//        return services.vaultAndUpdates().justSnapshot.filter { it.state.data is net.corda.iou.state.IOUState }
//    }
//
//    /**
//     * Displays all cash states that exist in the node's vault.
//     */
//    @GET
//    @Path("cash")
//    @Produces(MediaType.APPLICATION_JSON)
//    // Filter by state type: Cash.
//    fun getCash(): List<StateAndRef<ContractState>> {
//        return services.vaultAndUpdates().justSnapshot.filter { it.state.data is Cash.State }
//    }
//
//    /**
//     * Displays all cash states that exist in the node's vault.
//     */
//    @GET
//    @Path("cash-balances")
//    @Produces(MediaType.APPLICATION_JSON)
//    // Display cash balances.
//    fun getCashBalances(): Map<Currency, Amount<Currency>> = services.getCashBalances()

//    /**
//     * Initiates a flow to agree an IOU between two parties.
//     */
//    @GET
//    @Path("issue-obligation")
//    fun issueIOU(@QueryParam(value = "amount") amount: Int,
//                 @QueryParam(value = "currency") currency: String,
//                 @QueryParam(value = "party") party: String): Response {
//        // Get party objects for myself and the counterparty.
//        val me = services.nodeIdentity().legalIdentity
//        val lender = services.partyFromName(party) ?: throw IllegalArgumentException("Unknown party name.")
//        // Create a new IOU state using the parameters given.
//        val state = net.corda.iou.state.IOUState(Amount(amount.toLong() * 100, Currency.getInstance(currency)), lender, me)
//
//        // Start the IOUIssueFlow. We block and wait for the flow to return.
//        val (status, message) = try {
//            val flowHandle = services.startTrackedFlowDynamic(net.corda.iou.flow.IOUIssueFlow.Initiator::class.java, state, lender)
//            val result = flowHandle.use { it.returnValue.getOrThrow() }
//            // Return the response.
//            Response.Status.CREATED to "Transaction id ${result.id} committed to ledger.\n${result.tx.outputs.single()}"
//        } catch (e: Exception) {
//            // For the purposes of this demo app, we do not differentiate by exception type.
//            Response.Status.BAD_REQUEST to e.message
//        }
//
//        return Response.status(status).entity(message).build()
//    }
//
//    /**
//     * tranfers an IOU specified by [linearId] to a new party.
//     */
//    @GET
//    @Path("transfer-obligation")
//    fun transferIOU(@QueryParam(value = "id") id: String,
//                    @QueryParam(value = "party") party: String): Response {
//        val linearId = UniqueIdentifier.Companion.Companion.fromString(id)
//        val newLender = services.partyFromName(party) ?: throw IllegalArgumentException("Unknown party name.")
//
//        val (status, message) = try {
//            val flowHandle = services.startTrackedFlowDynamic(net.corda.iou.flow.IOUTransferFlow.Initiator::class.java, linearId, newLender)
//            // We don't care about the signed tx returned by the flow, only that it finishes successfully
//            flowHandle.use { flowHandle.returnValue.getOrThrow() }
//            Response.Status.CREATED to "IOU $id transferred to $party."
//        } catch (e: Exception) {
//            Response.Status.BAD_REQUEST to e.message
//        }
//
//        return Response.status(status).entity(message).build()
//    }
//
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
//    /**
//     * Helper end-point to issue some cash to ourselves.
//     */
//    @GET
//    @Path("self-issue-cash")
//    fun selfIssueCash(@QueryParam(value = "amount") amount: Int,
//                      @QueryParam(value = "currency") currency: String): Response {
//        val issueAmount = Amount(amount.toLong() * 100, Currency.getInstance(currency))
//
//        val (status, message) = try {
//            val flowHandle = services.startTrackedFlowDynamic(net.corda.iou.flow.SelfIssueCashFlow::class.java, issueAmount)
//            val cashState = flowHandle.use { it.returnValue.getOrThrow() }
//            Response.Status.CREATED to cashState.toString()
//        } catch (e: Exception) {
//            Response.Status.BAD_REQUEST to e.message
//        }
//
//        return Response.status(status).entity(message).build()
//    }

}