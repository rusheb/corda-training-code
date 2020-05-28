package com.template.contracts

import com.r3.corda.lib.tokens.contracts.FungibleTokenContract
import com.r3.corda.lib.tokens.contracts.commands.MoveTokenCommand
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.template.contracts.TokenContractK.Companion.TOKEN_CONTRACT_ID
import com.template.states.TokenStateK
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.transaction
import org.junit.Test
import kotlin.test.assertEquals

class TokenContractMoveTestsK {
    private val ledgerServices = MockServices()
    private val alice = TestIdentity(CordaX500Name("Alice", "London", "GB")).party
    private val bob = TestIdentity(CordaX500Name("Bob", "New York", "US")).party
    private val carly = TestIdentity(CordaX500Name("Carly", "New York", "US")).party
    private val aliceAirMile = IssuedTokenType(alice, TokenType("AirMile", 0))
    private val carlyAirMile = IssuedTokenType(carly, TokenType("AirMile", 0))

    @Test
    fun `Move transaction must be signed by current owner`() {
        ledgerServices.transaction {
            attachment("com.r3.corda.lib.tokens.contracts.FungibleTokenContract")
            input(FungibleTokenContract.contractId, FungibleToken(10 of aliceAirMile, bob))
            output(FungibleTokenContract.contractId, FungibleToken(10 of aliceAirMile, carly))
            tweak {
                command(alice.owningKey, MoveTokenCommand(aliceAirMile, inputs = listOf(0), outputs = listOf(0)))
                `fails with`("Required signers does not contain all the current owners")
            }
            command(bob.owningKey, MoveTokenCommand(aliceAirMile, inputs = listOf(0), outputs = listOf(0)))
            verifies()
        }
    }

    @Test
    fun `Move transaction must have inputs`() {
        ledgerServices.transaction {
            attachment("com.r3.corda.lib.tokens.contracts.FungibleTokenContract")
            output(FungibleTokenContract.contractId, FungibleToken(10 of aliceAirMile, carly))
            command(alice.owningKey, MoveTokenCommand(aliceAirMile, inputs = listOf(0), outputs = listOf(0)))
            `fails with`("There is a token group with no assigned command")
        }
    }

    @Test
    fun `Move transaction must have outputs`() {
        ledgerServices.transaction {
            input(TOKEN_CONTRACT_ID, TokenStateK(alice, bob, 10L))
            command(bob.owningKey, TokenContractK.Commands.Move())
            `fails with`("There should be moved tokens, in outputs.")
        }
    }

    @Test
    // Testing this may be redundant as these wrong states would have to be issued first, but the contract would not
    // let that happen.
    fun `Inputs may have a zero quantity`() {
        ledgerServices.transaction {
            attachment("com.r3.corda.lib.tokens.contracts.FungibleTokenContract")
            input(FungibleTokenContract.contractId, FungibleToken(10 of aliceAirMile, bob))
            input(FungibleTokenContract.contractId, FungibleToken(0 of aliceAirMile, bob))
            output(FungibleTokenContract.contractId, FungibleToken(10 of aliceAirMile, carly))
            command(bob.owningKey, MoveTokenCommand(aliceAirMile, inputs = listOf(0, 1), outputs = listOf(0)))
            verifies()
        }
    }

    @Test
    fun `Outputs must not have a zero quantity`() {
        ledgerServices.transaction {
            attachment("com.r3.corda.lib.tokens.contracts.FungibleTokenContract")
            input(FungibleTokenContract.contractId, FungibleToken(10 of aliceAirMile, bob))
            input(FungibleTokenContract.contractId, FungibleToken(10 of aliceAirMile, bob))
            output(FungibleTokenContract.contractId, FungibleToken(0 of aliceAirMile, carly))
            tweak {
                command(bob.owningKey, MoveTokenCommand(aliceAirMile, inputs = listOf(0, 1), outputs = listOf(0)))
                `fails with`("In move groups there must be an amount of output tokens > ZERO")
            }
            output(FungibleTokenContract.contractId, FungibleToken(20 of aliceAirMile, carly))
            command(bob.owningKey, MoveTokenCommand(aliceAirMile, inputs = listOf(0, 1), outputs = listOf(0, 1)))
            `fails with`("You cannot create output token amounts with a ZERO amount")
        }
    }

    @Test
    fun `All sums per issuer must be conserved in Move transaction`() {
        ledgerServices.transaction {
            attachment("com.r3.corda.lib.tokens.contracts.FungibleTokenContract")
            input(FungibleTokenContract.contractId, FungibleToken(10 of aliceAirMile, bob))
            input(FungibleTokenContract.contractId, FungibleToken(15 of aliceAirMile, bob))
            output(FungibleTokenContract.contractId, FungibleToken(25 of aliceAirMile, bob))
            command(bob.owningKey, MoveTokenCommand(aliceAirMile, inputs = listOf(0, 1), outputs = listOf(0)))
            tweak {
                input(FungibleTokenContract.contractId, FungibleToken(10 of carlyAirMile, bob))
                input(FungibleTokenContract.contractId, FungibleToken(15 of carlyAirMile, bob))
                command(bob.owningKey, MoveTokenCommand(carlyAirMile, inputs = listOf(2, 3), outputs = listOf(1)))
                tweak {
                    output(FungibleTokenContract.contractId, FungibleToken(30 of carlyAirMile, bob))
                    `fails with`("In move groups the amount of input tokens MUST EQUAL the amount of output tokens")
                }
                output(FungibleTokenContract.contractId, FungibleToken(25 of carlyAirMile, bob))
                verifies()
            }
            verifies()
        }
    }

    @Test
    fun `Sums cannot result in overflow`() {
        ledgerServices.transaction {
            attachment("com.r3.corda.lib.tokens.contracts.FungibleTokenContract")
            input(FungibleTokenContract.contractId, FungibleToken(Long.MAX_VALUE of aliceAirMile, bob))
            input(FungibleTokenContract.contractId, FungibleToken(1 of aliceAirMile, carly))
            output(FungibleTokenContract.contractId, FungibleToken(1 of aliceAirMile, bob))
            output(FungibleTokenContract.contractId, FungibleToken(Long.MAX_VALUE of aliceAirMile, carly))
            command(
                listOf(bob.owningKey, carly.owningKey),
                MoveTokenCommand(aliceAirMile, inputs = listOf(0, 1), outputs = listOf(0, 1))
            )
            `fails with`("Contract verification failed: long overflow")
        }
    }
}
