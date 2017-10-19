package net.corda.examples.obligation.contract

import net.corda.core.contracts.CommandData
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.examples.obligation.Obligation
import net.corda.finance.DOLLARS
import net.corda.finance.POUNDS
import net.corda.testing.*
import org.junit.After
import org.junit.Before

/**
 * A base class to reduce the boilerplate when writing obligation contract tests.
 */
abstract class ObligationContractUnitTests {
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
    protected val tenDollarObligationWithNewLender = Obligation(10.DOLLARS, CHARLIE, BOB)
    protected val tenDollarObligationWithNewBorrower = Obligation(10.DOLLARS, ALICE, ALICE)
    protected val tenDollarObligationWithNewAmount = Obligation(0.DOLLARS, ALICE, BOB)
}
