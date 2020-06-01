package com.template.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.heldBy
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.workflows.flows.rpc.IssueTokens
import com.template.states.TokenStateK
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.node.StatesToRecord
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker

object IssueFlowsK {

    @InitiatingFlow
    @StartableByRPC
    /**
     * Started by the [TokenStateK.issuer] to issue multiple states where it is the only issuer.
     * Because it is an [InitiatingFlow], its counterpart flow [Responder] is called automatically.
     * This constructor would typically be called by RPC or by [FlowLogic.subFlow].
     */

    class Initiator(private val heldQuantities: List<Pair<AbstractParty, Long>>) : FlowLogic<SignedTransaction>() {

        /**
         * The only constructor that can be called from the CLI.
         * Started by the issuer to issue a single state.
         */
        constructor(holder: AbstractParty, quantity: Long) : this(listOf(Pair(holder, quantity)))

        init {
            require(heldQuantities.isNotEmpty()) { "heldQuantities cannot be empty" }
            val noneZero = heldQuantities.none { it.second <= 0 }
            require(noneZero) { "heldQuantities must all be above 0" }
        }

        @Suppress("ClassName")
        companion object {
            object GENERATING_TRANSACTION : ProgressTracker.Step("Generating transaction based on parameters.")
            object FINALISING_TRANSACTION :
                ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(
                GENERATING_TRANSACTION,
                FINALISING_TRANSACTION
            )
        }

        override val progressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {

            progressTracker.currentStep = GENERATING_TRANSACTION
            val airMile = TokenType("AirMile", 0)
            val outputTokens = heldQuantities.map { (owner, amount) ->
                amount of airMile issuedBy ourIdentity heldBy owner
            }

            progressTracker.currentStep = FINALISING_TRANSACTION
            return subFlow(IssueTokens(outputTokens))
                .also { notarised ->
                    // We want our issuer to have a trace of the amounts that have been issued, whether it is a holder or not,
                    // in order to know the total supply. Since the issuer is not in the participants, it needs to be done
                    // manually.
                    serviceHub.recordTransactions(StatesToRecord.ALL_VISIBLE, listOf(notarised))
                }
        }
    }
}
