package net.corda.examples.obligation;

import com.google.common.collect.ImmutableSet;
import net.corda.core.contracts.*;
import net.corda.core.identity.AbstractParty;
import net.corda.core.transactions.LedgerTransaction;
import net.corda.finance.contracts.asset.Cash;

import static java.util.stream.Collectors.toSet;
import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;
import static net.corda.core.contracts.ContractsDSL.requireThat;

import java.security.PublicKey;
import java.util.Set;

public class ObligationContract implements Contract {
    public static final String OBLIGATION_CONTRACT_ID = "ObligationContract";

    public interface Commands extends CommandData {
        class Issue extends TypeOnlyCommandData implements Commands {}
        class Transfer extends TypeOnlyCommandData implements Commands {}
        class Settle extends TypeOnlyCommandData implements Commands {}
    }

    @Override public void verify(LedgerTransaction tx) {
        final CommandWithParties<Commands> command = requireSingleCommand(tx.getCommands(), Commands.class);
        final Commands commandData = command.getValue();
        final Set<PublicKey> setOfSigners = ImmutableSet.copyOf(command.getSigners());
        if (commandData instanceof Commands.Issue) {
            verifyIssue(tx, setOfSigners);
        } else if (commandData instanceof Commands.Transfer) {
            verifyTransfer(tx, setOfSigners);
        } else if (commandData instanceof Commands.Settle) {
            verifySettle(tx, setOfSigners);
        } else {
            throw new IllegalArgumentException("Unrecognised command.");
        }
    }

    private Set<PublicKey> keysFromParticipants(Obligation obligation) {
        return obligation
                .getParticipants().stream()
                .map(AbstractParty::getOwningKey)
                .collect(toSet());
    }

    // This only allows one obligation issuance per transaction.
    private void verifyIssue(LedgerTransaction tx, Set<PublicKey> signers) {
        requireThat(req -> {
            req.using("No inputs should be consumed when issuing an obligation.",
                    tx.getInputStates().isEmpty());
            "Only one obligation state should be created when issuing an obligation." using(tx.outputStates.size == 1)
            val obligation = tx.outputStates.single() as Obligation
            "A newly issued obligation must have a positive amount." using
                    (obligation.amount > Amount(0, obligation.amount.token))
            "The lender and borrower cannot be the same identity." using(obligation.borrower != obligation.lender)
            "Both lender and borrower together only may sign obligation issue transaction." using
                    (signers == keysFromParticipants(obligation))
            return null;
        });
    }




    // This only allows one obligation transfer per transaction.
    private fun verifyTransfer(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        "An obligation transfer transaction should only consume one input state." using (tx.inputs.size == 1)
        "An obligation transfer transaction should only create one output state." using (tx.outputs.size == 1)
        val input = tx.inputStates.single() as Obligation
        val output = tx.outputStates.single() as Obligation
        "Only the lender property may change." using (input.withoutLender() == output.withoutLender())
        "The lender property must change in a transfer." using (input.lender != output.lender)
        "The borrower, old lender and new lender only must sign an obligation transfer transaction" using
                (signers == (keysFromParticipants(input) `union` keysFromParticipants(output)))
    }

    private fun verifySettle(tx: LedgerTransaction, signers: Set<PublicKey>) = requireThat {
        // Check for the presence of an input obligation state.
        val obligationInputs = tx.inputsOfType<Obligation>()
        require(obligationInputs.size == 1) { "There must be one input obligation." }

        // Check there are output cash states.
        // We don't care about cash inputs, the Cash contract handles those.
        val cash = tx.outputsOfType<Cash.State>()
        require(cash.isNotEmpty()) { "There must be output cash." }

        // Check that the cash is being assigned to us.
        val inputObligation = obligationInputs.single()
        val acceptableCash = cash.filter { it.owner == inputObligation.lender }
        require(acceptableCash.isNotEmpty()) { "There must be output cash paid to the recipient." }

        // Sum the cash being sent to us (we don't care about the issuer).
        val sumAcceptableCash = acceptableCash.sumCash().withoutIssuer()
        val amountOutstanding = inputObligation.amount - inputObligation.paid
        require(amountOutstanding >= sumAcceptableCash) {
            "The amount settled cannot be more than the amount outstanding."
        }

        val obligationOutputs = tx.outputsOfType<Obligation>()

        // Check to see if we need an output obligation or not.
        if (amountOutstanding == sumAcceptableCash) {
            // If the obligation has been fully settled then there should be no obligation output state.
            require(obligationOutputs.isEmpty()) { "There must be no output obligation as it has been fully settled." }
        } else {
            // If the obligation has been partially settled then it should still exist.
            require(obligationOutputs.size == 1) { "There must be one output obligation." }

            // Check only the paid property changes.
            val outputObligation = obligationOutputs.single()
            requireThat {
                "The amount may not change when settling." using (inputObligation.amount == outputObligation.amount)
                "The borrower may not change when settling." using (inputObligation.borrower == outputObligation.borrower)
                "The lender may not change when settling." using (inputObligation.lender == outputObligation.lender)
                "The linearId may not change when settling." using (inputObligation.linearId == outputObligation.linearId)
            }

            // Check the paid property is updated correctly.
            require(outputObligation.paid == inputObligation.paid + sumAcceptableCash) {
                "Paid property incorrectly updated."
            }
        }

        // Checks the required parties have signed.
        "Both lender and borrower together only must sign obligation settle transaction." using
                (signers == keysFromParticipants(inputObligation))
    }
}