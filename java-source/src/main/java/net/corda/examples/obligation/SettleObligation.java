package net.corda.examples.obligation;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.corda.confidential.IdentitySyncFlow;
import net.corda.core.contracts.Amount;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.StateAndRef;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.node.services.vault.QueryCriteria.LinearStateQueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;
import net.corda.finance.contracts.asset.Cash;

import java.security.PublicKey;
import java.util.Currency;
import java.util.List;
import java.util.stream.Collectors;

import static net.corda.finance.contracts.GetBalances.getCashBalance;

public class SettleObligation {
    @InitiatingFlow
    @StartableByRPC
    static class Initiator extends FlowLogic<SignedTransaction> {
        private final UniqueIdentifier linearId;
        private final Amount<Currency> amount;
        private final Boolean anonymous;
        private final Party ourIdentity = getOurIdentity();


        private final Step PREPARATION = new Step("Obtaining IOU from vault.");
        private final Step BUILDING = new Step("Building and verifying transaction.");
        private final Step SIGNING = new Step("Signing transaction.");
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
                PREPARATION, BUILDING, SIGNING, COLLECTING, FINALISING
        );

        public Initiator(UniqueIdentifier linearId, Amount<Currency> amount, Boolean anonymous) {
            this.linearId = linearId;
            this.amount = amount;
            this.anonymous = anonymous;
        }

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        private Party resolveIdentity(AbstractParty abstractParty) {
            return getServiceHub().getIdentityService().requireWellKnownPartyFromAnonymous(abstractParty);
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
            StateAndRef<Obligation> obligationToSettle = obligations.get(0);
            Obligation inputObligation = obligationToSettle.getState().getData();

            // Stage 2. Resolve the lender and borrower identity if the obligation is anonymous.
            Party borrowerIdentity = resolveIdentity(inputObligation.getBorrower());
            Party lenderIdentity = resolveIdentity(inputObligation.getLender());

            // Stage 3. This flow can only be initiated by the current recipient.
            if (ourIdentity != borrowerIdentity) {
                throw new FlowException("Settle Obligation flow must be initiated by the borrower.");
            }

            // Stage 4. Check we have enough cash to settle the requested amount.
            Amount<Currency> cashBalance = getCashBalance(getServiceHub(), amount.getToken());
            if (cashBalance.getQuantity() > 0L) {
                throw new FlowException(String.format("Borrower has no %s to settle.", amount.getToken()));
            }

            Amount<Currency> amountLeftToSettle = inputObligation.getAmount().minus(inputObligation.getPaid());
            if (cashBalance.getQuantity() < amount.getQuantity()) {
                throw new FlowException(String.format(
                        "Borrower has only %s but needs %s to settle.", cashBalance, amount));
            }

            if (amountLeftToSettle.getQuantity() < amount.getQuantity()) {
                throw new FlowException(String.format(
                        "There's only %s but you pledged %s.", amountLeftToSettle, amount));
            }

            // Stage 5. Create a settle command.
            List<PublicKey> requiredSigners = inputObligation.getParticipants().stream().map(AbstractParty::getOwningKey).collect(Collectors.toList());
            Command settleCommand = new Command<>(
                    new ObligationContract.Commands.Settle(),
                    requiredSigners
            );

            // Stage 6. Create a transaction builder. Add the settle command and input obligation.
            progressTracker.setCurrentStep(BUILDING);
            List<Party> notaries = getServiceHub().getNetworkMapCache().getNotaryIdentities();
            if (notaries.isEmpty()) {
                throw new FlowException("No available notary.");
            }
            final Party notary = notaries.get(0);
            TransactionBuilder builder = new TransactionBuilder(notary)
                    .addInputState(obligationToSettle)
                    .addCommand(settleCommand);

            // Stage 7. Get some cash from the vault and add a spend to our transaction builder.
            List<PublicKey> cashSigningKeys = Cash.generateSpend(getServiceHub(), builder, amount, inputObligation.getLender(), ImmutableSet.of()).getSecond();

            // Stage 8. Only add an output obligation state if the obligation has not been fully settled.
            Amount<Currency> amountRemaining = amountLeftToSettle.minus(amount);
            if (amountRemaining.getQuantity() > 0) {
                Obligation outputObligation = inputObligation.pay(amount);
                builder.addOutputState(outputObligation, ObligationContract.OBLIGATION_CONTRACT_ID);
            }

            // Stage 9. Verify and sign the transaction.
            progressTracker.setCurrentStep(SIGNING);
            builder.verify(getServiceHub());
            List<PublicKey> signingKeys = ImmutableList.copyOf(cashSigningKeys);
            signingKeys.add(inputObligation.getBorrower().getOwningKey());
            SignedTransaction ptx = getServiceHub().signInitialTransaction(builder, signingKeys);

            // Stage 10. Get counterparty signature.
            progressTracker.setCurrentStep(COLLECTING);
            FlowSession session = initiateFlow(lenderIdentity);
            subFlow(new IdentitySyncFlow.Send(session, ptx.getTx()));
            SignedTransaction stx = subFlow(new CollectSignaturesFlow(
                    ptx,
                    ImmutableSet.of(session),
                    signingKeys,
                    COLLECTING.childProgressTracker()));

            // Stage 11. Finalize the transaction.
            progressTracker.setCurrentStep(FINALISING);
            return subFlow(new FinalityFlow(stx, FINALISING.childProgressTracker()));
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
