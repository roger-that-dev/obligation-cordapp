package net.corda.examples.obligation.flows

import net.corda.examples.obligation.Obligation
import net.corda.finance.POUNDS
import net.corda.testing.chooseIdentity
import org.junit.Test
import kotlin.test.assertEquals

class IssueObligationTests : ObligationTests() {

    @Test
    fun `Issue non-anonymous obligation successfully`() {
        val stx = issueObligation(a, b, 1000.POUNDS, anonymous = false)

        network.waitQuiescent()

        val aObligation = a.services.loadState(stx.tx.outRef<Obligation>(0).ref).data as Obligation
        val bObligation = b.services.loadState(stx.tx.outRef<Obligation>(0).ref).data as Obligation

        assertEquals(aObligation, bObligation)
    }


    @Test
    fun `issue anonymous obligation successfully`() {
        val stx = issueObligation(a, b, 1000.POUNDS)

        val aIdentity = a.services.myInfo.chooseIdentity()
        val bIdentity = b.services.myInfo.chooseIdentity()

        network.waitQuiescent()

        val aObligation = a.services.loadState(stx.tx.outRef<Obligation>(0).ref).data as Obligation
        val bObligation = b.services.loadState(stx.tx.outRef<Obligation>(0).ref).data as Obligation

        assertEquals(aObligation, bObligation)

        val maybePartyAlookedUpByA = a.services.identityService.requireWellKnownPartyFromAnonymous(aObligation.borrower)
        val maybePartyAlookedUpByB = b.services.identityService.requireWellKnownPartyFromAnonymous(aObligation.borrower)

        assertEquals(aIdentity, maybePartyAlookedUpByA)
        assertEquals(aIdentity, maybePartyAlookedUpByB)

        val maybePartyClookedUpByA = a.services.identityService.requireWellKnownPartyFromAnonymous(aObligation.lender)
        val maybePartyClookedUpByB = b.services.identityService.requireWellKnownPartyFromAnonymous(aObligation.lender)

        assertEquals(bIdentity, maybePartyClookedUpByA)
        assertEquals(bIdentity, maybePartyClookedUpByB)
    }
}
