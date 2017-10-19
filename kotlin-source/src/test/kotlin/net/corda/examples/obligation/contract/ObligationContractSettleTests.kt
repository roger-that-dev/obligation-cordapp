package net.corda.examples.obligation.contract

import net.corda.core.contracts.Amount
import net.corda.core.identity.AbstractParty
import net.corda.core.utilities.OpaqueBytes
import net.corda.examples.obligation.Obligation
import net.corda.examples.obligation.ObligationContract
import net.corda.examples.obligation.ObligationContract.Companion.OBLIGATION_CONTRACT_ID
import net.corda.finance.DOLLARS
import net.corda.finance.POUNDS
import net.corda.finance.`issued by`
import net.corda.finance.contracts.asset.Cash
import net.corda.testing.*
import org.junit.Test
import java.util.*

class ObligationContractSettleTests : ObligationContractUnitTests() {

    private val defaultRef = OpaqueBytes(ByteArray(1))
    private val defaultIssuer = MEGA_CORP.ref(defaultRef)

    private fun createCashState(amount: Amount<Currency>, owner: AbstractParty): Cash.State {
        return Cash.State(amount = amount `issued by` defaultIssuer, owner = owner)
    }

    @Test
    fun mustIncludeSettleCommand() {
        val inputCash = createCashState(5.DOLLARS, BOB)
        val outputCash = inputCash.withNewOwner(newOwner = ALICE).ownableState
        ledger {
            transaction {
                input(OBLIGATION_CONTRACT_ID) { tenDollarObligation }
                output(OBLIGATION_CONTRACT_ID) { tenDollarObligation.pay(5.DOLLARS) }
                input(OBLIGATION_CONTRACT_ID) { inputCash }
                output(OBLIGATION_CONTRACT_ID) { outputCash }
                command(BOB_PUBKEY) { Cash.Commands.Move() }
                this.fails()
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { tenDollarObligation }
                output(OBLIGATION_CONTRACT_ID) { tenDollarObligation.pay(5.DOLLARS) }
                input(OBLIGATION_CONTRACT_ID) { inputCash }
                output(OBLIGATION_CONTRACT_ID) { outputCash }
                command(BOB_PUBKEY) { Cash.Commands.Move() }
                command(ALICE_PUBKEY, BOB_PUBKEY) { DummyCommand() } // Wrong type.
                this.fails()
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { tenDollarObligation }
                output(OBLIGATION_CONTRACT_ID) { tenDollarObligation.pay(5.DOLLARS) }
                input(OBLIGATION_CONTRACT_ID) { inputCash }
                output(OBLIGATION_CONTRACT_ID) { outputCash }
                command(BOB_PUBKEY) { Cash.Commands.Move() }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ObligationContract.Commands.Settle() } // Correct Type.
                this.verifies()
            }
        }
    }

    @Test
    fun `must have only one input obligation`() {
        val duplicateObligation = Obligation(10.DOLLARS, ALICE, BOB)
        val tenDollars = createCashState(10.DOLLARS, BOB)
        val fiveDollars = createCashState(5.DOLLARS, BOB)
        ledger {
            transaction {
                command(ALICE_PUBKEY, BOB_PUBKEY) { ObligationContract.Commands.Settle() }
                output(OBLIGATION_CONTRACT_ID) { tenDollarObligation }
                this `fails with` "There must be one input obligation."
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { tenDollarObligation }
                input(OBLIGATION_CONTRACT_ID) { duplicateObligation }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ObligationContract.Commands.Settle() }
                output(OBLIGATION_CONTRACT_ID) { tenDollarObligation.pay(5.DOLLARS) }
                input(OBLIGATION_CONTRACT_ID) { fiveDollars }
                output(OBLIGATION_CONTRACT_ID) { fiveDollars.withNewOwner(newOwner = ALICE).ownableState }
                command(BOB_PUBKEY) { Cash.Commands.Move() }
                this `fails with` "There must be one input obligation."
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { tenDollarObligation }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ObligationContract.Commands.Settle() }
                input(OBLIGATION_CONTRACT_ID) { tenDollars }
                output(OBLIGATION_CONTRACT_ID) { tenDollars.withNewOwner(newOwner = ALICE).ownableState }
                command(BOB_PUBKEY) { Cash.Commands.Move() }
                this.verifies()
            }
        }
    }

    @Test
    fun `must be cash output states present`() {
        val cash = createCashState(5.DOLLARS, BOB)
        val cashPayment = cash.withNewOwner(newOwner = ALICE)
        ledger {
            transaction {
                input(OBLIGATION_CONTRACT_ID) { tenDollarObligation }
                output(OBLIGATION_CONTRACT_ID) { tenDollarObligation.pay(5.DOLLARS) }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ObligationContract.Commands.Settle() }
                this `fails with` "There must be output cash."
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { tenDollarObligation }
                input(OBLIGATION_CONTRACT_ID) { cash }
                output(OBLIGATION_CONTRACT_ID) { tenDollarObligation.pay(5.DOLLARS) }
                output(OBLIGATION_CONTRACT_ID) { cashPayment.ownableState }
                command(BOB_PUBKEY) { cashPayment.command }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ObligationContract.Commands.Settle() }
                this.verifies()
            }
        }
    }

    @Test
    fun `must be cash output states with receipient as owner`() {
        val cash = createCashState(5.DOLLARS, BOB)
        val invalidCashPayment = cash.withNewOwner(newOwner = CHARLIE)
        val validCashPayment = cash.withNewOwner(newOwner = ALICE)
        ledger {
            transaction {
                input(OBLIGATION_CONTRACT_ID) { tenDollarObligation }
                input(OBLIGATION_CONTRACT_ID) { cash }
                output(OBLIGATION_CONTRACT_ID) { tenDollarObligation.pay(5.DOLLARS) }
                output(OBLIGATION_CONTRACT_ID) { invalidCashPayment.ownableState }
                command(BOB_PUBKEY) { invalidCashPayment.command }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ObligationContract.Commands.Settle() }
                this `fails with` "There must be output cash paid to the recipient."
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { tenDollarObligation }
                input(OBLIGATION_CONTRACT_ID) { cash }
                output(OBLIGATION_CONTRACT_ID) { tenDollarObligation.pay(5.DOLLARS) }
                output(OBLIGATION_CONTRACT_ID) { validCashPayment.ownableState }
                command(BOB_PUBKEY) { validCashPayment.command }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ObligationContract.Commands.Settle() }
                this.verifies()
            }
        }
    }

    @Test
    fun `cash settlement amount must be less than the remaining amount`() {
        val elevenDollars = createCashState(11.DOLLARS, BOB)
        val tenDollars = createCashState(10.DOLLARS, BOB)
        val fiveDollars = createCashState(5.DOLLARS, BOB)
        ledger {
            transaction {
                input(OBLIGATION_CONTRACT_ID) { tenDollarObligation }
                input(OBLIGATION_CONTRACT_ID) { elevenDollars }
                output(OBLIGATION_CONTRACT_ID) { tenDollarObligation.pay(11.DOLLARS) }
                output(OBLIGATION_CONTRACT_ID) { elevenDollars.withNewOwner(newOwner = ALICE).ownableState }
                command(BOB_PUBKEY) { elevenDollars.withNewOwner(newOwner = ALICE).command }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ObligationContract.Commands.Settle() }
                this `fails with` "The amount settled cannot be more than the amount outstanding."
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { tenDollarObligation }
                input(OBLIGATION_CONTRACT_ID) { fiveDollars }
                output(OBLIGATION_CONTRACT_ID) { tenDollarObligation.pay(5.DOLLARS) }
                output(OBLIGATION_CONTRACT_ID) { fiveDollars.withNewOwner(newOwner = ALICE).ownableState }
                command(BOB_PUBKEY) { fiveDollars.withNewOwner(newOwner = ALICE).command }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ObligationContract.Commands.Settle() }
                this.verifies()
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { tenDollarObligation }
                input(OBLIGATION_CONTRACT_ID) { tenDollars }
                output(OBLIGATION_CONTRACT_ID) { tenDollars.withNewOwner(newOwner = ALICE).ownableState }
                command(BOB_PUBKEY) { tenDollars.withNewOwner(newOwner = ALICE).command }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ObligationContract.Commands.Settle() }
                this.verifies()
            }
        }
    }

    @Test
    fun `cash settlement must be in the correct currency`() {
        val tenDollars = createCashState(10.DOLLARS, BOB)
        val tenPounds = createCashState(10.POUNDS, BOB)
        ledger {
            transaction {
                input(OBLIGATION_CONTRACT_ID) { tenDollarObligation }
                input(OBLIGATION_CONTRACT_ID) { tenPounds }
                output(OBLIGATION_CONTRACT_ID) { tenPounds.withNewOwner(newOwner = ALICE).ownableState }
                command(BOB_PUBKEY) { tenPounds.withNewOwner(newOwner = ALICE).command }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ObligationContract.Commands.Settle() }
                this `fails with` "Token mismatch: GBP vs USD"
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { tenDollarObligation }
                input(OBLIGATION_CONTRACT_ID) { tenDollars }
                output(OBLIGATION_CONTRACT_ID) { tenDollars.withNewOwner(newOwner = ALICE).ownableState }
                command(BOB_PUBKEY) { tenDollars.withNewOwner(newOwner = ALICE).command }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ObligationContract.Commands.Settle() }
                this.verifies()
            }
        }
    }

    @Test
    fun `must have output obligation if not fully settling`() {
        val tenDollars = createCashState(10.DOLLARS, BOB)
        val fiveDollars = createCashState(5.DOLLARS, BOB)
        ledger {
            transaction {
                input(OBLIGATION_CONTRACT_ID) { tenDollarObligation }
                input(OBLIGATION_CONTRACT_ID) { fiveDollars }
                output(OBLIGATION_CONTRACT_ID) { fiveDollars.withNewOwner(newOwner = ALICE).ownableState }
                command(BOB_PUBKEY) { fiveDollars.withNewOwner(newOwner = BOB).command }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ObligationContract.Commands.Settle() }
                this `fails with` "There must be one output obligation."
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { tenDollarObligation }
                input(OBLIGATION_CONTRACT_ID) { fiveDollars }
                output(OBLIGATION_CONTRACT_ID) { fiveDollars.withNewOwner(newOwner = ALICE).ownableState }
                output(OBLIGATION_CONTRACT_ID) { tenDollarObligation.pay(5.DOLLARS) }
                command(BOB_PUBKEY) { fiveDollars.withNewOwner(newOwner = BOB).command }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ObligationContract.Commands.Settle() }
                verifies()
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { tenDollars }
                input(OBLIGATION_CONTRACT_ID) { tenDollarObligation }
                output(OBLIGATION_CONTRACT_ID) { tenDollarObligation.pay(10.DOLLARS) }
                output(OBLIGATION_CONTRACT_ID) { tenDollars.withNewOwner(newOwner = ALICE).ownableState }
                command(BOB_PUBKEY) { tenDollars.withNewOwner(newOwner = BOB).command }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ObligationContract.Commands.Settle() }
                this `fails with` "There must be no output obligation as it has been fully settled."
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { tenDollars }
                input(OBLIGATION_CONTRACT_ID) { tenDollarObligation }
                output(OBLIGATION_CONTRACT_ID) { tenDollars.withNewOwner(newOwner = ALICE).ownableState }
                command(BOB_PUBKEY) { tenDollars.withNewOwner(newOwner = BOB).command }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ObligationContract.Commands.Settle() }
                verifies()
            }
        }
    }

    @Test
    fun `only paid property may change`() {
        val fiveDollars = createCashState(5.DOLLARS, BOB)
        ledger {
            transaction {
                input(OBLIGATION_CONTRACT_ID) { tenDollarObligation }
                input(OBLIGATION_CONTRACT_ID) { fiveDollars }
                output(OBLIGATION_CONTRACT_ID) { fiveDollars.withNewOwner(newOwner = ALICE).ownableState }
                output(OBLIGATION_CONTRACT_ID) { tenDollarObligation.copy(borrower = ALICE, paid = 5.DOLLARS) }
                command(BOB_PUBKEY) { fiveDollars.withNewOwner(newOwner = BOB).command }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ObligationContract.Commands.Settle() }
                this `fails with` "The borrower may not change when settling."
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { tenDollarObligation }
                input(OBLIGATION_CONTRACT_ID) { fiveDollars }
                output(OBLIGATION_CONTRACT_ID) { fiveDollars.withNewOwner(newOwner = ALICE).ownableState }
                output(OBLIGATION_CONTRACT_ID) { tenDollarObligation.copy(amount = 0.DOLLARS, paid = 5.DOLLARS) }
                command(BOB_PUBKEY) { fiveDollars.withNewOwner(newOwner = BOB).command }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ObligationContract.Commands.Settle() }
                this `fails with` "The amount may not change when settling."
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { tenDollarObligation }
                input(OBLIGATION_CONTRACT_ID) { fiveDollars }
                output(OBLIGATION_CONTRACT_ID) { fiveDollars.withNewOwner(newOwner = ALICE).ownableState }
                output(OBLIGATION_CONTRACT_ID) { tenDollarObligation.copy(lender = CHARLIE, paid = 5.DOLLARS) }
                command(BOB_PUBKEY) { fiveDollars.withNewOwner(newOwner = BOB).command }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ObligationContract.Commands.Settle() }
                this `fails with` "The lender may not change when settling."
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { tenDollarObligation }
                input(OBLIGATION_CONTRACT_ID) { fiveDollars }
                output(OBLIGATION_CONTRACT_ID) { fiveDollars.withNewOwner(newOwner = ALICE).ownableState }
                output(OBLIGATION_CONTRACT_ID) { tenDollarObligation.pay(5.DOLLARS) }
                command(BOB_PUBKEY) { fiveDollars.withNewOwner(newOwner = BOB).command }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ObligationContract.Commands.Settle() }
                verifies()
            }
        }
    }

    @Test
    fun `must be signed by all participants`() {
        val cash = createCashState(5.DOLLARS, BOB)
        val cashPayment = cash.withNewOwner(newOwner = ALICE)
        ledger {
            transaction {
                input(OBLIGATION_CONTRACT_ID) { cash }
                input(OBLIGATION_CONTRACT_ID) { tenDollarObligation }
                output(OBLIGATION_CONTRACT_ID) { cashPayment.ownableState }
                command(BOB_PUBKEY) { cashPayment.command }
                output(OBLIGATION_CONTRACT_ID) { tenDollarObligation.pay(5.DOLLARS) }
                command(ALICE_PUBKEY, CHARLIE_PUBKEY) { ObligationContract.Commands.Settle() }
                failsWith("Both lender and borrower together only must sign obligation settle transaction.")
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { cash }
                input(OBLIGATION_CONTRACT_ID) { tenDollarObligation }
                output(OBLIGATION_CONTRACT_ID) { cashPayment.ownableState }
                command(BOB_PUBKEY) { cashPayment.command }
                output(OBLIGATION_CONTRACT_ID) { tenDollarObligation.pay(5.DOLLARS) }
                command(BOB_PUBKEY) { ObligationContract.Commands.Settle() }
                failsWith("Both lender and borrower together only must sign obligation settle transaction.")
            }
            transaction {
                input(OBLIGATION_CONTRACT_ID) { cash }
                input(OBLIGATION_CONTRACT_ID) { tenDollarObligation }
                output(OBLIGATION_CONTRACT_ID) { cashPayment.ownableState }
                command(BOB_PUBKEY) { cashPayment.command }
                output(OBLIGATION_CONTRACT_ID) { tenDollarObligation.pay(5.DOLLARS) }
                command(ALICE_PUBKEY, BOB_PUBKEY) { ObligationContract.Commands.Settle() }
                verifies()
            }
        }
    }
}