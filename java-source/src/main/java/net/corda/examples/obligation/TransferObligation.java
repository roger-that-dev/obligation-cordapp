package net.corda.examples.obligation;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.corda.confidential.IdentitySyncFlow;
import net.corda.confidential.SwapIdentitiesFlow;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.AnonymousParty;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.node.services.vault.QueryCriteria.LinearStateQueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;

import java.security.PublicKey;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static net.corda.finance.contracts.asset.ObligationKt.OBLIGATION_PROGRAM_ID;

public class TransferObligation {

    @StartableByRPC
    @InitiatingFlow
    static class Initiator extends FlowLogic<SignedTransaction> {
        private final UniqueIdentifier linearId;
        private final Party newLender;
        private final Boolean anonymous;
        private final Party ourIdentity = getOurIdentity();

        private final Step PREPARATION = new Step("Obtaining IOU from vault.");
        private final Step BUILDING = new Step("Building and verifying transaction.");
        private final Step SIGNING = new Step("Signing transaction.");
        private final Step SYNCING = new Step("Syncing identities.");
        private final Step COLLECTING = new Step("Collecting counterparty signature.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return CollectSignaturesFlow.Companion.tracker();
            }
        };
        private final Step FINALISING = new Step("Finalising transaction.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        private final ProgressTracker progressTracker = new ProgressTracker(
                PREPARATION, BUILDING, SIGNING, SYNCING, COLLECTING, FINALISING
        );

        public Initiator(UniqueIdentifier linearId, Party newLender, Boolean anonymous) {
            this.linearId = linearId;
            this.newLender = newLender;
            this.anonymous = anonymous;
        }

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // Stage 1. Retrieve obligation specified by linearId from the vault.
            progressTracker.setCurrentStep(PREPARATION);
            QueryCriteria queryCriteria = new LinearStateQueryCriteria(
                    null,
                    ImmutableList.of(linearId),
                    Vault.StateStatus.UNCONSUMED,
                    null);
            List<StateAndRef<Obligation>> obligations = getServiceHub().getVaultService().queryBy(Obligation.class, queryCriteria).getStates();
            if (obligations.size() != 1) {
                throw new FlowException(String.format("Obligation with id %s not found.", linearId));
            }
            StateAndRef<Obligation> obligationToTransfer = obligations.get(0);
            Obligation inputObligation = obligationToTransfer.getState().getData();

            // Stage 2. This flow can only be initiated by the current recipient.
            AbstractParty lenderIdentity;
            if (inputObligation.getLender() instanceof AnonymousParty) {
                lenderIdentity = getServiceHub().getIdentityService().requireWellKnownPartyFromAnonymous(inputObligation.getLender());
            } else {
                lenderIdentity = inputObligation.getLender();
            }

            // Stage 3. Abort if the borrower started this flow.
            if (ourIdentity != lenderIdentity) {
                throw new IllegalStateException("Obligation transfer can only be initiated by the lender.");
            }

            // Stage 4. Create the new obligation state reflecting a new lender.
            progressTracker.setCurrentStep(BUILDING);
            Obligation transferredObligation;
            if (anonymous) {
                // TODO: Is there a flow to get a key and cert only from the counterparty?
                HashMap<Party, AnonymousParty> txKeys = subFlow(new SwapIdentitiesFlow(newLender));
                if (!txKeys.containsKey(newLender)) {
                    throw new FlowException("Couldn't get lender's conf. identity.");
                }
                AnonymousParty anonymousLender = txKeys.get(newLender);
                transferredObligation = inputObligation.withNewLender(anonymousLender);
            } else {
                transferredObligation = inputObligation.withNewLender(newLender);
            }

            // Stage 4. Create the transfer command.
            List<AbstractParty> signers = ImmutableList.copyOf(inputObligation.getParticipants());
            signers.add(transferredObligation.getLender());
            List<PublicKey> signerKeys = signers.stream().map(AbstractParty::getOwningKey).collect(Collectors.toList());
            Command transferCommand = new Command<>(new ObligationContract.Commands.Transfer(), signerKeys);

            // Stage 5. Get a reference to a notary
            List<Party> notaries = getServiceHub().getNetworkMapCache().getNotaryIdentities();
            if (notaries.isEmpty()) {
                throw new FlowException("No available notary.");
            }
            final Party notary = notaries.get(0);

            // Stage 6. Create a transaction builder, then add the states and commands.
            TransactionBuilder builder = new TransactionBuilder(notary)
                    .addInputState(obligationToTransfer)
                    .addOutputState(transferredObligation, OBLIGATION_PROGRAM_ID)
                    .addCommand(transferCommand);

            // Stage 7. Verify and sign the transaction.
            progressTracker.setCurrentStep(SIGNING);
            builder.verify(getServiceHub());
            SignedTransaction ptx = getServiceHub().signInitialTransaction(builder, inputObligation.getLender().getOwningKey());

            // Stage 8. Get a Party object for the borrower.
            progressTracker.setCurrentStep(SYNCING);
            Party borrower;
            if (inputObligation.getBorrower() instanceof AnonymousParty) {
                borrower = getServiceHub().getIdentityService().requireWellKnownPartyFromAnonymous(inputObligation.getBorrower());
            } else {
                borrower = (Party) inputObligation.getBorrower();
            }

            // Stage 9. Send any keys and certificates so the signers can verify each other's identity.
            List<Party> counterparties = ImmutableList.of(borrower, newLender);
            Set<FlowSession> sessions = counterparties.stream().map(this::initiateFlow).collect(Collectors.toSet());
            subFlow(new IdentitySyncFlow.Send(sessions, ptx.getTx(), SYNCING.childProgressTracker()));

            // Stage 10. Collect signatures from the borrower and the new lender.
            progressTracker.setCurrentStep(COLLECTING);
            SignedTransaction stx = subFlow(new CollectSignaturesFlow(
                    ptx,
                    sessions,
                    ImmutableList.of(inputObligation.getLender().getOwningKey()),
                    COLLECTING.childProgressTracker()));

            // Stage 11. Notarise and record, the transaction in our vaults. Send a copy to me as well.
            progressTracker.setCurrentStep(FINALISING);
            return subFlow(new FinalityFlow(stx, ImmutableSet.of(ourIdentity)));
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