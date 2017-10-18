package net.corda.examples.obligation;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.corda.confidential.SwapIdentitiesFlow;
import net.corda.core.contracts.Amount;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;

import java.security.PublicKey;
import java.time.Duration;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class IssueObligation {
    @InitiatingFlow
    @StartableByRPC
    static class Initiator extends FlowLogic<SignedTransaction> {
        private final Amount<Currency> amount;
        private final Party lender;
        private final Boolean anonymous;
        private final Party ourIdentity = getOurIdentity();

        private final Step INITIALISING = new Step("Performing initial steps.");
        private final Step BUILDING = new Step("Performing initial steps.");
        private final Step SIGNING = new Step("Signing transaction.");
        private final Step COLLECTING = new Step("Collecting counterparty signature.") {
            @Override public ProgressTracker childProgressTracker() {
                return CollectSignaturesFlow.Companion.tracker();
            }
        };
        private final Step FINALISING = new Step("Finalising transaction.") {
            @Override public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        private final ProgressTracker progressTracker = new ProgressTracker(
                INITIALISING, BUILDING, SIGNING, COLLECTING, FINALISING
        );

        public Initiator(Amount<Currency> amount, Party lender, Boolean anonymous) {
            this.amount = amount;
            this.lender = lender;
            this.anonymous = anonymous;
        }

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        @Suspendable
        public Obligation createAnonymousObligation() throws FlowException {
            HashMap<Party, AnonymousParty> txKeys = subFlow(new SwapIdentitiesFlow(lender));

            if (txKeys.size() != 2) {
                throw new IllegalStateException("Something went wrong when generating confidential identities.");
            }

            if (!txKeys.containsKey(ourIdentity)) {
                throw new FlowException("Couldn't create our conf. identity.");
            }

            if (!txKeys.containsKey(lender)) {
                throw new FlowException("Couldn't create lender's conf. identity.");
            }

            AnonymousParty anonymousMe = txKeys.get(ourIdentity);
            AnonymousParty anonymousLender = txKeys.get(lender);

            return new Obligation(amount, anonymousLender, anonymousMe);
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // Step 1. Initialisation.
            progressTracker.setCurrentStep(INITIALISING);
            Obligation obligation;
            if (anonymous) {
                obligation = createAnonymousObligation();
            } else {
                obligation = new Obligation(amount, lender, ourIdentity);
            }
            final PublicKey ourSigningKey = obligation.getBorrower().getOwningKey();

            // Step 2. Building.
            progressTracker.setCurrentStep(BUILDING);
            List<Party> notaries = getServiceHub().getNetworkMapCache().getNotaryIdentities();
            if (notaries.isEmpty()) {
                throw new FlowException("No available notary.");
            }
            final Party notary = notaries.get(0);

            final List<PublicKey> requiredSigners = obligation.getParticipants()
                    .stream()
                    .map(AbstractParty::getOwningKey)
                    .collect(Collectors.toList());

            TransactionBuilder utx = new TransactionBuilder(notary)
                    .addOutputState(obligation, ObligationContract.OBLIGATION_CONTRACT_ID)
                    .addCommand(new ObligationContract.Commands.Issue(), requiredSigners)
                    .setTimeWindow(getServiceHub().getClock().instant(), Duration.ofSeconds(30));

            // Step 3. Sign the transaction.
            progressTracker.setCurrentStep(SIGNING);
            SignedTransaction ptx = getServiceHub().signInitialTransaction(utx, ourSigningKey);

            // Step 4. Get the counter-party signature.
            progressTracker.setCurrentStep(COLLECTING);
            FlowSession lenderFlow = initiateFlow(lender);
            SignedTransaction stx = subFlow(new CollectSignaturesFlow(
                    ptx,
                    ImmutableSet.of(lenderFlow),
                    ImmutableList.of(ourSigningKey),
                    COLLECTING.childProgressTracker())
            );

            // Step 5. Finalise the transaction.
            progressTracker.setCurrentStep(FINALISING);
            SignedTransaction ftx = subFlow(new FinalityFlow(stx, FINALISING.childProgressTracker()));

            // Step 6. Return the finalised transaction.
            return ftx;
        }
    }

    @InitiatedBy(Initiator.class)
    static class Responder extends FlowLogic<SignedTransaction> {

        private final FlowSession otherFlow;

        public Responder(FlowSession otherFlow) {
            this.otherFlow = otherFlow;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            class SignTxFlow extends SignTransactionFlow {
                private SignTxFlow(FlowSession otherFlow, ProgressTracker progressTracker) {
                    super(otherFlow, progressTracker);
                }

                @Override
                protected void checkTransaction(SignedTransaction tx) {

                }
            }

            SignedTransaction stx = subFlow(new SignTxFlow(otherFlow, SignTransactionFlow.Companion.tracker()));
            return waitForLedgerCommit(stx.getId());
        }
    }
}
