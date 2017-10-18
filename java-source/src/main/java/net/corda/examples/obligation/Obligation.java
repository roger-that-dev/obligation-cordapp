package net.corda.examples.obligation;

import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.crypto.NullKeys;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;

import java.security.PublicKey;
import java.util.Currency;
import java.util.List;

import static net.corda.core.utilities.EncodingUtils.toBase58String;

public class Obligation implements LinearState {
    private final Amount<Currency> amount;
    private final AbstractParty lender;
    private final AbstractParty borrower;
    private final Amount<Currency> paid;
    private final UniqueIdentifier linearId;

    public Obligation(Amount<Currency> amount,
                      AbstractParty lender,
                      AbstractParty borrower,
                      Amount<Currency> paid,
                      UniqueIdentifier linearId) {
        this.amount = amount;
        this.lender = lender;
        this.borrower = borrower;
        this.paid = paid;
        this.linearId = linearId;
    }

    public Amount<Currency> getAmount() {
        return amount;
    }

    public AbstractParty getLender() {
        return lender;
    }

    public AbstractParty getBorrower() {
        return borrower;
    }

    public Amount<Currency> getPaid() {
        return paid;
    }

    @Override
    public UniqueIdentifier getLinearId() {
        return linearId;
    }

    @Override
    public List<AbstractParty> getParticipants() {
        return ImmutableList.of(lender, borrower);
    }

    public Obligation pay(Amount<Currency> amountToPay) {
        return new Obligation(
                this.amount,
                this.lender,
                this.borrower,
                this.paid.plus(amountToPay),
                this.linearId
        );
    }

    public Obligation withNewLender(AbstractParty newLender) {
        return new Obligation(this.amount, newLender, this.borrower, this.paid, this.linearId);
    }

    public Obligation withoutLender() {
        return new Obligation(this.amount, NullKeys.INSTANCE.getNULL_PARTY(), this.borrower, this.paid, this.linearId);
    }

    @Override
    public String toString() {
        String lenderString;
        if (this.lender instanceof Party) {
            lenderString = ((Party) lender).getName().getOrganisation();
        } else {
            PublicKey lenderKey = this.lender.getOwningKey();
            lenderString = toBase58String(lenderKey);
        }

        String borrowerString;
        if (this.borrower instanceof Party) {
            borrowerString = ((Party) borrower).getName().getOrganisation();
        } else {
            PublicKey borrowerKey = this.borrower.getOwningKey();
            borrowerString = toBase58String(borrowerKey);
        }

        return String.format("Obligation(%s): %s owes %s %s and has paid %s so far.",
                this.linearId, borrowerString, lenderString, this.amount, this.paid);
    }
}