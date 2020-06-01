package com.template.flows

import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.issuedBy
import com.r3.corda.lib.tokens.contracts.utilities.of
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import kotlin.test.assertEquals

fun createFrom(
    issuer: StartedMockNode,
    holder: StartedMockNode,
    quantity: Long
) = FungibleToken(
    amount = quantity of TokenType("AirMile", 0) issuedBy issuer.info.singleIdentity(),
    holder = holder.info.singleIdentity()
)

fun FungibleToken.toPair() = Pair(holder, amount.quantity)

fun StartedMockNode.assertHasStatesInVault(tokenStates: List<FungibleToken>) {
    val vaultTokens = transaction {
        services.vaultService.queryBy(FungibleToken::class.java).states
    }
    assertEquals(tokenStates.size, vaultTokens.size)
    assertEquals(tokenStates, vaultTokens.map { it.state.data })
}

class NodeHolding(

    val holder: StartedMockNode,
    val quantity: Long
) {
    fun toPair() = Pair(holder.info.singleIdentity(), quantity)
}

fun StartedMockNode.issueTokens(network: MockNetwork, nodeHoldings: Collection<NodeHolding>) =
    IssueFlowsK.Initiator(nodeHoldings.map(NodeHolding::toPair))
        .let { startFlow(it) }
        .also { network.runNetwork() }
        .getOrThrow()
        .toLedgerTransaction(services)
        .outRefsOfType<FungibleToken>()
