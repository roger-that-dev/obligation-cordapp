package net.corda.iou.state

import net.corda.core.contracts.*
import net.corda.core.identity.Party
import net.corda.core.utilities.ALICE
import net.corda.core.utilities.BOB
import net.corda.core.utilities.CHARLIE
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class IOUStateTests {
    @Test
    fun hasIOUAmountFieldOfCorrectType() {
        // Does the amount field exist?
        IOUState::class.java.getDeclaredField("amount")
        // Is the amount field of the correct type?
        assertEquals(IOUState::class.java.getDeclaredField("amount").type, Amount::class.java)
    }

    @Test
    fun hasLenderFieldOfCorrectType() {
        // Does the lender field exist?
        IOUState::class.java.getDeclaredField("lender")
        // Is the lender field of the correct type?
        assertEquals(IOUState::class.java.getDeclaredField("lender").type, Party::class.java)
    }

    @Test
    fun hasBorrowerFieldOfCorrectType() {
        // Does the borrower field exist?
        IOUState::class.java.getDeclaredField("borrower")
        // Is the borrower field of the correct type?
        assertEquals(IOUState::class.java.getDeclaredField("borrower").type, Party::class.java)
    }

    @Test
    fun hasPaidFieldOfCorrectType() {
        // Does the paid field exist?
        IOUState::class.java.getDeclaredField("paid")
        // Is the paid field of the correct type?
        assertEquals(IOUState::class.java.getDeclaredField("paid").type, Amount::class.java)
        // Does the paid field's currency match the amount field's currency?
        val iouStateGBP = IOUState(1.POUNDS, ALICE, BOB)
        val iouStateUSD = IOUState(1.DOLLARS, ALICE, BOB)
        assertEquals(iouStateGBP.amount.token, iouStateGBP.paid.token)
        assertEquals(iouStateUSD.amount.token, iouStateUSD.paid.token)
    }

    @Test
    fun lenderIsParticipant() {
        val iouState = IOUState(1.POUNDS, ALICE, BOB)
        assertNotEquals(iouState.participants.indexOf(ALICE), -1)
    }

    @Test
    fun borrowerIsParticipant() {
        val iouState = IOUState(1.POUNDS, ALICE, BOB)
        assertNotEquals(iouState.participants.indexOf(BOB), -1)
    }

    @Test
    fun isLinearState() {
        assert(LinearState::class.java.isAssignableFrom(IOUState::class.java))
    }

    @Test
    fun hasLinearIdFieldOfCorrectType() {
        // Does the linearId field exist?
        IOUState::class.java.getDeclaredField("linearId")
        // Is the paid field of the correct type?
        assertEquals(IOUState::class.java.getDeclaredField("linearId").type, UniqueIdentifier::class.java)
    }

    @Test
    fun isRelevantMethodComplete() {
        val iouState = IOUState(1.POUNDS, ALICE, BOB)
        assert(iouState.isRelevant(setOf(ALICE.owningKey, BOB.owningKey)))
    }

    @Test
    fun checkIOUStateParameterOrdering() {
        val fields = IOUState::class.java.declaredFields
        assertEquals(fields[0], IOUState::class.java.getDeclaredField("amount"))
        assertEquals(fields[1], IOUState::class.java.getDeclaredField("lender"))
        assertEquals(fields[2], IOUState::class.java.getDeclaredField("borrower"))
        assertEquals(fields[3], IOUState::class.java.getDeclaredField("paid"))
        assertEquals(fields[4], IOUState::class.java.getDeclaredField("linearId"))
    }

    @Test
    fun checkIOUStateToStringMethod() {
        val iouState = IOUState(1.POUNDS, ALICE, BOB)
        assertEquals(iouState.toString(), "IOU(${iouState.linearId}): CN=Bob Plc,O=Bob Plc,L=Rome,C=IT owes CN=Alice Corp,O=Alice Corp,L=Madrid,C=ES 1.00 GBP and has paid 0.00 GBP so far.")
    }

    @Test
    fun checkPayHelperMethod() {
        val iou = IOUState(10.DOLLARS, ALICE, BOB)
        assertEquals(5.DOLLARS, iou.pay(5.DOLLARS).paid)
        assertEquals(3.DOLLARS, iou.pay(1.DOLLARS).pay(2.DOLLARS).paid)
        assertEquals(10.DOLLARS, iou.pay(5.DOLLARS).pay(3.DOLLARS).pay(2.DOLLARS).paid)
    }

    @Test
    fun checkWithNewLenderHelperMethod() {
        val iou = IOUState(10.DOLLARS, ALICE, BOB)
        assertEquals(CHARLIE, iou.withNewLender(CHARLIE).lender)
    }
}