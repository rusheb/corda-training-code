package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.workflows.flows.move.AbstractMoveTokensFlow
import com.r3.corda.lib.tokens.workflows.flows.move.addMoveTokens
import com.r3.corda.lib.tokens.workflows.internal.flows.distribution.UpdateDistributionListFlow
import com.r3.corda.lib.tokens.workflows.internal.flows.finality.ObserverAwareFinalityFlow
import com.r3.corda.lib.tokens.workflows.utilities.toParty
import com.template.states.TokenStateK
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

object MoveFlowsK {

    @CordaSerializable
    /**
     * The different transaction roles expected of the responder.
     * A signer needs to sign, a participant only needs to receive the result.
     */
    enum class TransactionRole { SIGNER, PARTICIPANT }

    @InitiatingFlow
    @StartableByRPC
    /**
     * Started by a [TokenStateK.holder] to move multiple states where it is one of the holders.
     * Because it is an [InitiatingFlow], its counterpart flow [Responder] is called automatically.
     * This constructor would be called by RPC or by [FlowLogic.subFlow]. In particular one that, given sums, fetches
     * states in the vault.
     */
    class Initiator @JvmOverloads constructor(
        private val inputTokens: List<StateAndRef<FungibleToken>>,
        private val outputTokens: List<FungibleToken>,
        override val progressTracker: ProgressTracker = tracker()
    ) : FlowLogic<SignedTransaction>() {

        init {
            require(inputTokens.isNotEmpty()) { "inputTokens cannot be empty" }
            require(outputTokens.isNotEmpty()) { "outputTokens cannot be empty" }
            val noneZero = outputTokens.none { it.amount.quantity <= 0 }
            require(noneZero) { "outputTokens quantities must all be above 0" }
        }

        @Suppress("ClassName")
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on parameters.")
            object VERIFYING_TRANSACTION : ProgressTracker.Step("Verifying contract constraints.")
            object SIGNING_TRANSACTION : ProgressTracker.Step("Signing transaction with our private key.")
            object GATHERING_SIGS : ProgressTracker.Step("Gathering the counterparty's signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING_TRANSACTION :
                ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
            )
        }

        @Suspendable
        override fun call(): SignedTransaction {
            val airMile = TokenType("AirMile", 0)

            val oldHolders = inputTokens
                .map { it.state.data.holder.toParty(serviceHub) }
                .toSet()

            val oldHolderSessions = oldHolders.map { signer ->
                initiateFlow(signer)
            }

            val newHolders = outputTokens
                .map { it.holder.toParty(serviceHub) }
                .distinct()
                .minus(oldHolders)
                .minus(ourIdentity)

            val newHolderSessions = newHolders.map { initiateFlow(it) }

            val allSessions = oldHolderSessions + newHolderSessions

            // return subFlow(MoveTokensFlow(inputTokens, outputTokens, allSessions, listOf()))

            class MySubFlow() : AbstractMoveTokensFlow() {
                override val observerSessions: List<FlowSession>
                    get() = listOf()
                override val participantSessions: List<FlowSession>
                    get() = allSessions

                override fun addMove(transactionBuilder: TransactionBuilder) {
                    addMoveTokens(transactionBuilder, inputTokens, outputTokens)
                }
            }

            return subFlow(MySubFlow())

//            val transactionBuilder = TransactionBuilder()
//
//            addMoveTokens(transactionBuilder, inputTokens, outputTokens);
//            val signedTransaction = subFlow(
//                ObserverAwareFinalityFlow(
//                    transactionBuilder = transactionBuilder,
//                    allSessions = allSessions
//                )
//            )
////            progressTracker.currentStep = AbstractMoveTokensFlow.Companion.UPDATING
//            // Update the distribution list.
//            subFlow(UpdateDistributionListFlow(signedTransaction))
//            // Return the newly created transaction.
//            return signedTransaction

        }
    }
}