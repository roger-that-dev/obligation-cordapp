package net.corda.examples.obligation

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.finance.DOLLARS
import net.corda.finance.POUNDS
import net.corda.testing.ALICE
import net.corda.testing.BOB
import net.corda.testing.setCordappPackages
import net.corda.testing.unsetCordappPackages
import org.junit.After
import org.junit.Before

open class ObligationContractUnitTests {
    @Before
    fun setup() {
        setCordappPackages("net.corda.examples.obligation", "net.corda.testing.contracts")
    }

    @After
    fun tearDown() {
        unsetCordappPackages()
    }

    protected class DummyState : ContractState {
        override val participants: List<AbstractParty> get() = listOf()
    }

    protected class DummyCommand : CommandData

    protected val oneDollarObligation = Obligation(1.POUNDS, ALICE, BOB)
    protected val tenDollarObligation = Obligation(10.DOLLARS, ALICE, BOB)
}
