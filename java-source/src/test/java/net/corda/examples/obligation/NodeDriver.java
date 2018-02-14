package net.corda.examples.obligation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.corda.core.identity.CordaX500Name;
import net.corda.testing.driver.DriverParameters;
import net.corda.testing.driver.NodeHandle;
import net.corda.testing.driver.NodeParameters;
import net.corda.testing.node.User;

import static net.corda.testing.driver.Driver.driver;

public class NodeDriver {
    public static void main(String[] args) {
        final User user = new User("user1", "test", ImmutableSet.of("ALL"));

        driver(new DriverParameters().setIsDebug(true).setStartNodesInProcess(true).setWaitForAllNodesToFinish(true), dsl -> {
            try {
                NodeHandle nodeA = dsl.startNode(new NodeParameters()
                        .setProvidedName(new CordaX500Name("PartyA", "London", "GB"))
                        .setRpcUsers(ImmutableList.of(user))).get();
                NodeHandle nodeB = dsl.startNode(new NodeParameters()
                        .setProvidedName(new CordaX500Name("PartyB", "New York", "US"))
                        .setRpcUsers(ImmutableList.of(user))).get();
                NodeHandle nodeC = dsl.startNode(new NodeParameters()
                        .setProvidedName(new CordaX500Name("PartyC", "Paris", "FR"))
                        .setRpcUsers(ImmutableList.of(user))).get();

                dsl.startWebserver(nodeA);
                dsl.startWebserver(nodeB);
                dsl.startWebserver(nodeC);
            } catch (Throwable e) {
                System.err.println("Encountered exception in node startup: " + e.getMessage());
                e.printStackTrace();
            }
            return null;
        });
    }
}