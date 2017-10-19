package net.corda.examples.iou;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.identity.Party;
import net.corda.node.services.transactions.SimpleNotaryService;
import net.corda.nodeapi.internal.ServiceInfo;
import net.corda.testing.driver.DriverParameters;
import net.corda.testing.driver.NodeHandle;
import net.corda.testing.driver.NodeParameters;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static net.corda.testing.TestConstants.*;
import static net.corda.testing.driver.Driver.driver;

public class IntegrationTest {
    @Test
    public void runDriverTest() {
        Party notary = getDUMMY_NOTARY();
        Party bankA = getDUMMY_BANK_A();
        Party bankB = getDUMMY_BANK_B();
        Set<ServiceInfo> notaryServices = ImmutableSet.of(new ServiceInfo(SimpleNotaryService.Companion.getType(), null));

        driver(new DriverParameters().setIsDebug(true).setStartNodesInProcess(true), dsl -> {
            // This starts three nodes simultaneously with startNode, which returns a future that completes when the node
            // has completed startup. Then these are all resolved with getOrThrow which returns the NodeHandle list.
            List<CordaFuture<NodeHandle>> handles = ImmutableList.of(
                    dsl.startNode(new NodeParameters().setProvidedName(notary.getName()).setAdvertisedServices(notaryServices)),
                    dsl.startNode(new NodeParameters().setProvidedName(bankA.getName())),
                    dsl.startNode(new NodeParameters().setProvidedName(bankB.getName()))
            );

            try {
                NodeHandle notaryHandle = handles.get(0).get();
                NodeHandle nodeAHandle = handles.get(1).get();
                NodeHandle nodeBHandle = handles.get(2).get();

                // This test will call via the RPC proxy to find a party of another node to verify that the nodes have
                // started and can communicate. This is a very basic test, in practice tests would be starting flows,
                // and verifying the states in the vault and other important metrics to ensure that your CorDapp is working
                // as intended.
                Assert.assertEquals(notaryHandle.getRpc().wellKnownPartyFromX500Name(bankA.getName()).getName(), bankA.getName());
                Assert.assertEquals(notaryHandle.getRpc().wellKnownPartyFromX500Name(bankB.getName()).getName(), bankB.getName());
                Assert.assertEquals(notaryHandle.getRpc().wellKnownPartyFromX500Name(notary.getName()).getName(), notary.getName());
            } catch (Exception e) {
                throw new RuntimeException("Caught exception during test", e);
            }

            return null;
        });
    }
}