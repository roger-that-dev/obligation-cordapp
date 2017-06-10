package net.corda.iou.contract

import net.corda.contracts.asset.Cash
import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.serialization.OpaqueBytes
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.BOB
import net.corda.core.utilities.CHARLIE
import net.corda.iou.state.IOUState
import net.corda.testing.MEGA_CORP
import net.corda.testing.ledger
import org.junit.Test
import java.util.*

class IOUSettleTests {
    val defaultRef = OpaqueBytes(ByteArray(1, { 1 }))
    val defaultIssuer = MEGA_CORP.ref(defaultRef)

    private fun createCashState(amount: Amount<Currency>, owner: AbstractParty): Cash.State {
        return Cash.State(amount = amount `issued by` defaultIssuer, owner = owner)
    }

    // A pre-defined dummy command.
    class DummyCommand : TypeOnlyCommandData()

    @Test
    fun mustIncludeSettleCommand() {
        val iou = IOUState(10.POUNDS, ALICE, BOB)
        val inputCash = createCashState(5.POUNDS, BOB)
        val outputCash = inputCash.withNewOwner(newOwner = ALICE).second
        ledger {
            transaction {
                input { iou }
                output { iou.pay(5.POUNDS) }
                input { inputCash }
                output { outputCash }
                command(BOB.owningKey) { Cash.Commands.Move() }
                this.fails()
            }
            transaction {
                input { iou }
                output { iou.pay(5.POUNDS) }
                input { inputCash }
                output { outputCash }
                command(BOB.owningKey) { Cash.Commands.Move() }
                command(ALICE.owningKey, BOB.owningKey) { DummyCommand() } // Wrong type.
                this.fails()
            }
            transaction {
                input { iou }
                output { iou.pay(5.POUNDS) }
                input { inputCash }
                output { outputCash }
                command(BOB.owningKey) { Cash.Commands.Move() }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() } // Correct Type.
                this.verifies()
            }
        }
    }

    @Test
    fun mustBeOneGroupOfIOUs() {
        val iouOne = IOUState(10.POUNDS, ALICE, BOB)
        val iouTwo = IOUState(5.POUNDS, ALICE, BOB)
        val inputCash = createCashState(5.POUNDS, BOB)
        val outputCash = inputCash.withNewOwner(newOwner = ALICE).second
        ledger {
            transaction {
                input { iouOne }
                input { iouTwo }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                output { iouOne.pay(5.POUNDS) }
                input { inputCash }
                output { outputCash }
                command(BOB.owningKey) { Cash.Commands.Move() }
                this `fails with` "List has more than one element."
            }
            transaction {
                input { iouOne }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                output { iouOne.pay(5.POUNDS) }
                input { inputCash }
                output { outputCash }
                command(BOB.owningKey) { Cash.Commands.Move() }
                this.verifies()
            }
        }
    }

    @Test
    fun mustHaveOneInputIOU() {
        val iou = IOUState(1.POUNDS, ALICE, BOB)
        val iouOne = IOUState(10.POUNDS, ALICE, BOB)
        val tenPounds = createCashState(10.POUNDS, BOB)
        val fivePounds = createCashState(5.POUNDS, BOB)
        ledger {
            transaction {
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                output { iou }
                this `fails with` "There must be one input IOU."
            }
            transaction {
                input { iouOne }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                output { iouOne.pay(5.POUNDS) }
                input { fivePounds }
                output { fivePounds.withNewOwner(newOwner = ALICE).second }
                command(BOB.owningKey) { Cash.Commands.Move() }
                this.verifies()
            }
            transaction {
                input { iouOne }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                input { tenPounds }
                output { tenPounds.withNewOwner(newOwner = ALICE).second }
                command(BOB.owningKey) { Cash.Commands.Move() }
                this.verifies()
            }
        }
    }

    @Test
    fun mustBeCashOutputStatesPresent() {
        val iou = IOUState(10.DOLLARS, ALICE, BOB)
        val cash = createCashState(5.DOLLARS, BOB)
        val cashPayment = cash.withNewOwner(newOwner = ALICE)
        ledger {
            transaction {
                input { iou }
                output { iou.pay(5.DOLLARS) }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                this `fails with` "There must be output cash."
            }
            transaction {
                input { iou }
                input { cash }
                output { iou.pay(5.DOLLARS) }
                output { cashPayment.second }
                command(BOB.owningKey) { cashPayment.first }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                this.verifies()
            }
        }
    }

    @Test
    fun mustBeCashOutputStatesWithRecipientAsOwner() {
        val iou = IOUState(10.POUNDS, ALICE, BOB)
        val cash = createCashState(5.POUNDS, BOB)
        val invalidCashPayment = cash.withNewOwner(newOwner = CHARLIE)
        val validCashPayment = cash.withNewOwner(newOwner = ALICE)
        ledger {
            transaction {
                input { iou }
                input { cash }
                output { iou.pay(5.POUNDS) }
                output { invalidCashPayment.second }
                command(BOB.owningKey) { invalidCashPayment.first }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                this `fails with` "There must be output cash paid to the recipient."
            }
            transaction {
                input { iou }
                input { cash }
                output { iou.pay(5.POUNDS) }
                output { validCashPayment.second }
                command(BOB.owningKey) { validCashPayment.first }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                this.verifies()
            }
        }
    }

    @Test
    fun cashSettlementAmountMustBeLessThanRemainingIOUAmount() {
        val iou = IOUState(10.DOLLARS, ALICE, BOB)
        val elevenDollars = createCashState(11.DOLLARS, BOB)
        val tenDollars = createCashState(10.DOLLARS, BOB)
        val fiveDollars = createCashState(5.DOLLARS, BOB)
        ledger {
            transaction {
                input { iou }
                input { elevenDollars }
                output { iou.pay(11.DOLLARS) }
                output { elevenDollars.withNewOwner(newOwner = ALICE).second }
                command(BOB.owningKey) { elevenDollars.withNewOwner(newOwner = ALICE).first }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                this `fails with` "The amount settled cannot be more than the amount outstanding."
            }
            transaction {
                input { iou }
                input { fiveDollars }
                output { iou.pay(5.DOLLARS) }
                output { fiveDollars.withNewOwner(newOwner = ALICE).second }
                command(BOB.owningKey) { fiveDollars.withNewOwner(newOwner = ALICE).first }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                this.verifies()
            }
            transaction {
                input { iou }
                input { tenDollars }
                output { tenDollars.withNewOwner(newOwner = ALICE).second }
                command(BOB.owningKey) { tenDollars.withNewOwner(newOwner = ALICE).first }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                this.verifies()
            }
        }
    }

    @Test
    fun cashSettlementMustBeInTheCorrectCurrency() {
        val iou = IOUState(10.DOLLARS, ALICE, BOB)
        val tenDollars = createCashState(10.DOLLARS, BOB)
        val tenPounds = createCashState(10.POUNDS, BOB)
        ledger {
            transaction {
                input { iou }
                input { tenPounds }
                output { tenPounds.withNewOwner(newOwner = ALICE).second }
                command(BOB.owningKey) { tenPounds.withNewOwner(newOwner = ALICE).first }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                this `fails with` "Token mismatch: GBP vs USD"
            }
            transaction {
                input { iou }
                input { tenDollars }
                output { tenDollars.withNewOwner(newOwner = ALICE).second }
                command(BOB.owningKey) { tenDollars.withNewOwner(newOwner = ALICE).first }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                this.verifies()
            }
        }
    }


    @Test
    fun mustOnlyHaveOutputIOUIfNotFullySettling() {
        val iou = IOUState(10.DOLLARS, ALICE, BOB)
        val tenDollars = createCashState(10.DOLLARS, BOB)
        val fiveDollars = createCashState(5.DOLLARS, BOB)
        ledger {
            transaction {
                input { iou }
                input { fiveDollars }
                output { fiveDollars.withNewOwner(newOwner = ALICE).second }
                command(BOB.owningKey) { fiveDollars.withNewOwner(newOwner = BOB).first }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                this `fails with` "There must be one output IOU."
            }
            transaction {
                input { iou }
                input { fiveDollars }
                output { fiveDollars.withNewOwner(newOwner = ALICE).second }
                output { iou.pay(5.DOLLARS) }
                command(BOB.owningKey) { fiveDollars.withNewOwner(newOwner = BOB).first }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                verifies()
            }
            transaction {
                input { tenDollars }
                input { iou }
                output { iou.pay(10.DOLLARS) }
                output { tenDollars.withNewOwner(newOwner = ALICE).second }
                command(BOB.owningKey) { tenDollars.withNewOwner(newOwner = BOB).first }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                this `fails with` "There must be no output IOU as it has been fully settled."
            }
            transaction {
                input { tenDollars }
                input { iou }
                output { tenDollars.withNewOwner(newOwner = ALICE).second }
                command(BOB.owningKey) { tenDollars.withNewOwner(newOwner = BOB).first }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                verifies()
            }
        }
    }

    @Test
    fun onlyPaidPropertyMayChange() {
        val iou = IOUState(10.DOLLARS, ALICE, BOB)
        val fiveDollars = createCashState(5.DOLLARS, BOB)
        ledger {
            transaction {
                input { iou }
                input { fiveDollars }
                output { fiveDollars.withNewOwner(newOwner = ALICE).second }
                output { iou.copy(borrower = ALICE, paid = 5.DOLLARS) }
                command(BOB.owningKey) { fiveDollars.withNewOwner(newOwner = BOB).first }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                this `fails with` "The borrower may not change when settling."
            }
            transaction {
                input { iou }
                input { fiveDollars }
                output { fiveDollars.withNewOwner(newOwner = ALICE).second }
                output { iou.copy(amount = 0.DOLLARS, paid = 5.DOLLARS) }
                command(BOB.owningKey) { fiveDollars.withNewOwner(newOwner = BOB).first }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                this `fails with` "The amount may not change when settling."
            }
            transaction {
                input { iou }
                input { fiveDollars }
                output { fiveDollars.withNewOwner(newOwner = ALICE).second }
                output { iou.copy(lender = CHARLIE, paid = 5.DOLLARS) }
                command(BOB.owningKey) { fiveDollars.withNewOwner(newOwner = BOB).first }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                this `fails with` "The lender may not change when settling."
            }
            transaction {
                input { iou }
                input { fiveDollars }
                output { fiveDollars.withNewOwner(newOwner = ALICE).second }
                output { iou.pay(5.DOLLARS) }
                command(BOB.owningKey) { fiveDollars.withNewOwner(newOwner = BOB).first }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                verifies()
            }
        }
    }

    @Test
    fun paidMustBeCorrectlyUpdated() {
        val iou = IOUState(10.DOLLARS, ALICE, BOB)
        val fiveDollars = createCashState(5.DOLLARS, BOB)
        ledger {
            transaction {
                input { iou }
                input { fiveDollars }
                output { fiveDollars.withNewOwner(newOwner = ALICE).second }
                output { iou.copy(paid = 4.DOLLARS) }
                command(BOB.owningKey) { fiveDollars.withNewOwner(newOwner = BOB).first }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                this `fails with` "Paid property incorrectly updated."
            }
            transaction {
                input { iou }
                input { fiveDollars }
                output { fiveDollars.withNewOwner(newOwner = ALICE).second }
                output { iou.copy() }
                command(BOB.owningKey) { fiveDollars.withNewOwner(newOwner = BOB).first }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                this `fails with` "Paid property incorrectly updated."
            }
            transaction {
                input { iou }
                input { fiveDollars }
                output { fiveDollars.withNewOwner(newOwner = ALICE).second }
                output { iou.copy(paid = 10.DOLLARS) }
                command(BOB.owningKey) { fiveDollars.withNewOwner(newOwner = BOB).first }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                this `fails with` "Paid property incorrectly updated."
            }
            transaction {
                input { iou }
                input { fiveDollars }
                output { fiveDollars.withNewOwner(newOwner = ALICE).second }
                output { iou.pay(5.DOLLARS) }
                command(BOB.owningKey) { fiveDollars.withNewOwner(newOwner = BOB).first }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                verifies()
            }
        }
    }

    @Test
    fun mustBeSignedByAllParticipants() {
        val iou = IOUState(10.DOLLARS, ALICE, BOB)
        val cash = createCashState(5.DOLLARS, BOB)
        val cashPayment = cash.withNewOwner(newOwner = ALICE)
        ledger {
            transaction {
                input { cash }
                input { iou }
                output { cashPayment.second }
                command(BOB.owningKey) { cashPayment.first }
                output { iou.pay(5.DOLLARS) }
                command(ALICE.owningKey, CHARLIE.owningKey) { IOUContract.Commands.Settle() }
                failsWith("Both lender and borrower together only must sign IOU settle transaction.")
            }
            transaction {
                input { cash }
                input { iou }
                output { cashPayment.second }
                command(BOB.owningKey) { cashPayment.first }
                output { iou.pay(5.DOLLARS) }
                command(BOB.owningKey) { IOUContract.Commands.Settle() }
                failsWith("Both lender and borrower together only must sign IOU settle transaction.")
            }
            transaction {
                input { cash }
                input { iou }
                output { cashPayment.second }
                command(BOB.owningKey) { cashPayment.first }
                output { iou.pay(5.DOLLARS) }
                command(ALICE.owningKey, BOB.owningKey) { IOUContract.Commands.Settle() }
                verifies()
            }
        }
    }
}