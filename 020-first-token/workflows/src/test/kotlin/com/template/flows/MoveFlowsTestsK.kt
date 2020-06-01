package com.template.flows

import com.google.common.collect.ImmutableList
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.workflows.flows.move.MoveTokensFlowHandler
import net.corda.core.flows.FlowException
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.singleIdentity
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.MockNetworkNotarySpec
import net.corda.testing.node.MockNetworkParameters
import net.corda.testing.node.TestCordapp
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MoveFlowsTestsK {
    private val network = MockNetwork(
        MockNetworkParameters()
            .withNotarySpecs(ImmutableList.of(MockNetworkNotarySpec(Constants.desiredNotary)))
            .withCordappsForAllNodes(
                listOf(
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.contracts"),
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.workflows"),
                    TestCordapp.findCordapp("com.r3.corda.lib.tokens.selection"),
                    TestCordapp.findCordapp("com.template.contracts"),
                    TestCordapp.findCordapp("com.template.flows")
                )
            )
    )
    private val alice = network.createNode()
    private val bob = network.createNode()
    private val carly = network.createNode()
    private val dan = network.createNode()

    init {
        listOf(alice, bob, carly, dan).forEach {
//            it.registerInitiatedFlow(MoveFlowsK.Initiator::class.java, MoveFlowsK.Responder::class.java)
            it.registerInitiatedFlow(MoveFlowsK.Initiator::class.java, MoveTokensFlowHandler::class.java)
        }
    }

    @Before
    fun setup() = network.runNetwork()

    @After
    fun tearDown() = network.stopNodes()

    @Test
    fun `input tokens cannot be empty`() {
        assertFailsWith<IllegalArgumentException> {
            MoveFlowsK.Initiator(
                emptyList(), listOf(
                    createFrom(alice, dan, 10L),
                    createFrom(carly, bob, 20L)
                )
            )
        }
    }

    @Test
    fun outputTokensCannotBeEmpty() {
        val issuedTokens = alice.issueTokens(network, listOf(NodeHolding(bob, 10L)))
        assertFailsWith<IllegalArgumentException> {
            MoveFlowsK.Initiator(issuedTokens, emptyList())
        }
    }

    @Test
    fun outputTokensCannotHaveAnyZeroQuantity() {
        val issuedTokens = alice.issueTokens(network, listOf(NodeHolding(bob, 10L)))
        assertFailsWith<IllegalArgumentException> {
            MoveFlowsK.Initiator(
                issuedTokens, listOf(
                    createFrom(alice, dan, 10L),
                    createFrom(carly, bob, 0L)
                )
            )
        }
    }

    @Test
    fun `flow fails when initiator is missing transactions they were not party to`() {
        // alice issues bob 10 tokens and
        // carly issues dan 10 tokens
        val issuedTokens = alice.issueTokens(network, listOf(NodeHolding(bob, 10L)))
            .plus(carly.issueTokens(network, listOf(NodeHolding(dan, 20L))))

        val flow = MoveFlowsK.Initiator(
            issuedTokens, listOf(
                createFrom(alice, dan, 10L),
                createFrom(carly, bob, 20L)
            )
        )
        // bob tries to send his AliceTokens to dan
        // in exchange for dan's CarlyTokens
        val future = bob.startFlow(flow)
        network.runNetwork()
        // but Dan doesn't know about all the inputs because he wasn't party to Carly -> Dan
        assertFailsWith<FlowException> { future.getOrThrow() }
    }

    @Test
    fun `SignedTransaction returned by the flow is signed by the holder`() {
        // Alice issues 10 tokens to Bob
        val issuedTokens = alice.issueTokens(network, listOf(NodeHolding(bob, 10L)))

        // Bob tries moves his tokens to Carly
        val flow = MoveFlowsK.Initiator(issuedTokens, listOf(createFrom(alice, carly, 10L)))
        val future = bob.startFlow(flow)
        network.runNetwork()
        val tx = future.getOrThrow()
        // we check that Bob's signature is on the transaction
        tx.verifySignaturesExcept(listOf(alice.info.singleIdentity().owningKey, carly.info.singleIdentity().owningKey))
    }

    @Test
    fun `SignedTransaction returned by the flow is signed by both holders, same issuer`() {
        val issuedTokens = alice.issueTokens(
            network, listOf(
                NodeHolding(bob, 10L),
                NodeHolding(carly, 20L)
            )
        )

        // Bob and Carly both transfer their tokens to Dan
        val flow = MoveFlowsK.Initiator(issuedTokens, listOf(createFrom(alice, dan, 30L)))
        val future = bob.startFlow(flow)
        network.runNetwork()
        val tx = future.getOrThrow()
        tx.verifySignaturesExcept(alice.info.singleIdentity().owningKey)
    }

//    @Test
//    fun `flow records a transaction in holder transaction storages only`() {
//        val issuedTokens = alice.issueTokens(network, listOf(NodeHolding(bob, 10L)))
//
//        val flow = MoveFlowsK.Initiator(issuedTokens, listOf(createFrom(alice, carly, 10L)))
//        val future = bob.startFlow(flow)
//        network.runNetwork()
//        val tx = future.getOrThrow()
//
//        // We check the recorded transaction in both transaction storages.
//        for (node in listOf(bob, carly)) {
//            assertEquals(tx, node.services.validatedTransactions.getTransaction(tx.id))
//        }
//        for (node in listOf(alice, dan)) {
//            assertNull(node.services.validatedTransactions.getTransaction(tx.id))
//        }
//    }
//
//    @Test
//    fun `flow records a transaction in both holders transaction storages, same issuer`() {
//        val issuedTokens = alice.issueTokens(
//            network, listOf(
//                NodeHolding(bob, 10L),
//                NodeHolding(carly, 20L)
//            )
//        )
//
//        val flow = MoveFlowsK.Initiator(issuedTokens, listOf(createFrom(alice, dan, 30L)))
//        val future = bob.startFlow(flow)
//        network.runNetwork()
//        val tx = future.getOrThrow()
//
//        // We check the recorded transaction in 3 transaction storages.
//        for (node in listOf(bob, carly, dan)) {
//            assertEquals(tx, node.services.validatedTransactions.getTransaction(tx.id))
//        }
//        assertNull(alice.services.validatedTransactions.getTransaction(tx.id))
//    }
//
//    @Test
//    fun `recorded transaction has a single input and a single output`() {
//        val issuedTokens = alice.issueTokens(network, listOf(NodeHolding(bob, 10L)))
//        val expectedInput = issuedTokens[0].state.data
//        val expectedOutput = createFrom(alice, carly, 10L)
//
//        val flow = MoveFlowsK.Initiator(issuedTokens, listOf(expectedOutput))
//        val future = bob.startFlow(flow)
//        network.runNetwork()
//        val tx = future.getOrThrow()
//
//        // We check the recorded transaction in both vaults.
//        for (node in listOf(bob, carly)) {
//            val recordedTx = node.services.validatedTransactions.getTransaction(tx.id)!!
//            val txInputs = recordedTx.tx.inputs
//            assertEquals(1, txInputs.size)
//            assertEquals(expectedInput, node.services.toStateAndRef<FungibleToken>(txInputs[0]).state.data)
//            val txOutputs = recordedTx.tx.outputs
//            assertEquals(1, txOutputs.size)
//            assertEquals(expectedOutput, txOutputs[0].data as FungibleToken)
//        }
//        for (node in listOf(alice, dan)) {
//            assertNull(node.services.validatedTransactions.getTransaction(tx.id))
//        }
//    }
//
//    @Test
//    fun `recorded transaction has two inputs and 1 output, same issuer`() {
//        val issuedTokens = alice.issueTokens(
//            network, listOf(
//                NodeHolding(bob, 10L),
//                NodeHolding(carly, 20L)
//            )
//        )
//        val expectedInputs = issuedTokens.map { it.state.data }
//        val expectedOutput = createFrom(alice, dan, 30L)
//
//        val flow = MoveFlowsK.Initiator(issuedTokens, listOf(expectedOutput))
//        val future = bob.startFlow(flow)
//        network.runNetwork()
//        val tx = future.getOrThrow()
//
//        // We check the recorded transaction in 3 vaults.
//        for (node in listOf(bob, carly, dan)) {
//            val recordedTx = node.services.validatedTransactions.getTransaction(tx.id)!!
//            val txInputs = recordedTx.tx.inputs
//            assertEquals(2, txInputs.size)
//            assertEquals(expectedInputs, txInputs.map { node.services.toStateAndRef<FungibleToken>(it).state.data })
//            val txOutputs = recordedTx.tx.outputs
//            assertEquals(1, txOutputs.size)
//            assertEquals(expectedOutput, txOutputs[0].data as FungibleToken)
//        }
//        alice.services.validatedTransactions.getTransaction(tx.id)
//    }
//
//    @Test
//    fun `there is one recorded state after move only in recipient, issuer keeps old state`() {
//        val issuedTokens = alice.issueTokens(network, listOf(NodeHolding(bob, 10L)))
//        val expectedOutput = createFrom(alice, carly, 10L)
//
//        val flow = MoveFlowsK.Initiator(issuedTokens, listOf(expectedOutput))
//        val future = bob.startFlow(flow)
//        network.runNetwork()
//        future.getOrThrow()
//
//        // We check the states in vaults.
//        alice.assertHasStatesInVault(issuedTokens.map { it.state.data })
//        bob.assertHasStatesInVault(listOf())
//        carly.assertHasStatesInVault(listOf(expectedOutput))
//    }
//
//    @Test
//    fun `there is one recorded state after move only in recipient, same issuer, issuer keeps old states`() {
//        val issuedTokens = alice.issueTokens(
//            network, listOf(
//                NodeHolding(bob, 10L),
//                NodeHolding(carly, 20L)
//            )
//        )
//        val expectedOutput = createFrom(alice, dan, 30L)
//
//        val flow = MoveFlowsK.Initiator(issuedTokens, listOf(expectedOutput))
//        val future = bob.startFlow(flow)
//        network.runNetwork()
//        future.getOrThrow()
//
//        // We check the states in vaults.
//        alice.assertHasStatesInVault(issuedTokens.map { it.state.data })
//        bob.assertHasStatesInVault(listOf())
//        carly.assertHasStatesInVault(listOf())
//        dan.assertHasStatesInVault(listOf(expectedOutput))
//    }
//
}