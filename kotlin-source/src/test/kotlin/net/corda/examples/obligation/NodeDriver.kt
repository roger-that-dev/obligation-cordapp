package net.corda.examples.obligation

import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.nodeapi.User
import net.corda.nodeapi.internal.ServiceInfo
import net.corda.testing.driver.driver

fun main(args: Array<String>) {
    val user = User("user1", "test", permissions = setOf())

    driver(isDebug = true, startNodesInProcess = true) {
        startNode(
                providedName = CordaX500Name("Controller", "London", "GB"),
                advertisedServices = setOf(ServiceInfo(SimpleNotaryService.type))
        )

        val (nodeA, nodeB, nodeC) = listOf(
                startNode(providedName = CordaX500Name("PartyA", "London", "GB"), rpcUsers = listOf(user)),
                startNode(providedName = CordaX500Name("PartyB", "New York", "US"), rpcUsers = listOf(user)),
                startNode(providedName = CordaX500Name("PartyC", "Paris", "FR"), rpcUsers = listOf(user))
        ).map { it.getOrThrow() }

        startWebserver(nodeA)
        startWebserver(nodeB)
        startWebserver(nodeC)

        waitForAllNodesToFinish()
    }
}