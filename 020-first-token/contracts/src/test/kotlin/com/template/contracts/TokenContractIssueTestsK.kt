package com.template.contracts

import com.r3.corda.lib.tokens.contracts.FungibleTokenContract
import com.r3.corda.lib.tokens.contracts.commands.IssueTokenCommand
import com.r3.corda.lib.tokens.contracts.states.FungibleToken
import com.r3.corda.lib.tokens.contracts.types.IssuedTokenType
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.template.contracts.TokenContractK.Companion.TOKEN_CONTRACT_ID
import com.template.states.TokenStateK
import net.corda.core.contracts.Amount
import net.corda.core.identity.CordaX500Name
import net.corda.testing.contracts.DummyContract
import net.corda.testing.contracts.DummyState
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.transaction
import org.junit.Test

class TokenContractIssueTestsK {
    private val ledgerServices = MockServices()
    private val alice = TestIdentity(CordaX500Name("Alice", "London", "GB")).party
    private val bob = TestIdentity(CordaX500Name("Bob", "New York", "US")).party
    private val carly = TestIdentity(CordaX500Name("Carly", "New York", "US")).party
    private val aliceAirMile = IssuedTokenType(alice, TokenType("AirMile", 0))
    private val carlyAirMile = IssuedTokenType(carly, TokenType("AirMile", 0))

    @Test
    fun `transaction must include a TokenContract command`() {
        ledgerServices.transaction {
            attachment("com.r3.corda.lib.tokens.contracts.FungibleTokenContract")
            output(FungibleTokenContract.contractId, FungibleToken(10 of aliceAirMile, bob))
            tweak {
                command(alice.owningKey, DummyContract.Commands.Create())
                `fails with`("There must be at least one token command in this transaction")
            }
            command(alice.owningKey, IssueTokenCommand(aliceAirMile, outputs = listOf(0)))
            verifies()
        }
    }

    @Test
    fun `Issue transaction must have no inputs`() {
        ledgerServices.transaction {
            attachment("com.r3.corda.lib.tokens.contracts.FungibleTokenContract")
            input(FungibleTokenContract.contractId, FungibleToken(10 of aliceAirMile, carly))
            output(FungibleTokenContract.contractId, FungibleToken(10 of aliceAirMile, bob))
            command(alice.owningKey, IssueTokenCommand(aliceAirMile, outputs = listOf(0)))
            `fails with`("There is a token group with no assigned command")
        }
    }

    @Test
    fun `Issue transaction may have no outputs`() {
        ledgerServices.transaction {
            attachment("com.r3.corda.lib.tokens.contracts.FungibleTokenContract")
            output(FungibleTokenContract.contractId, DummyState())
            command(alice.owningKey, IssueTokenCommand(aliceAirMile, outputs = listOf(0)))
            verifies()
        }
    }

    @Test
    fun `Outputs must not have a zero quantity`() {
        ledgerServices.transaction {
            attachment("com.r3.corda.lib.tokens.contracts.FungibleTokenContract")
            output(FungibleTokenContract.contractId, FungibleToken(0 of aliceAirMile, bob))
            command(alice.owningKey, IssueTokenCommand(aliceAirMile, outputs = listOf(0)))
            `fails with`("When issuing tokens an amount > ZERO must be issued")
        }
    }

    @Test
    fun `Issuer must sign Issue transaction`() {
        ledgerServices.transaction {
            attachment("com.r3.corda.lib.tokens.contracts.FungibleTokenContract")
            output(FungibleTokenContract.contractId, FungibleToken(10 of aliceAirMile, bob))
            command(bob.owningKey, IssueTokenCommand(aliceAirMile, outputs = listOf(0)))
            `fails with`("The issuer must be the signing party when an amount of tokens are issued")
        }
    }

   @Test
   fun `Other parties may also sign Issue transaction`() {
       ledgerServices.transaction {
           attachment("com.r3.corda.lib.tokens.contracts.FungibleTokenContract")
           output(FungibleTokenContract.contractId, FungibleToken(10 of aliceAirMile, bob))
           command(listOf(alice.owningKey, bob.owningKey, carly.owningKey), IssueTokenCommand(aliceAirMile, outputs = listOf(0)))
           verifies()
       }
   }

    @Test
    fun `All issuers must sign Issue transaction`() {
        ledgerServices.transaction {
            attachment("com.r3.corda.lib.tokens.contracts.FungibleTokenContract")
            output(FungibleTokenContract.contractId, FungibleToken(10 of aliceAirMile, bob))
            output(FungibleTokenContract.contractId, FungibleToken(10 of carlyAirMile, bob))
            command(alice.owningKey, IssueTokenCommand(aliceAirMile, outputs = listOf(0)))
            tweak {
                command(alice.owningKey, IssueTokenCommand(carlyAirMile, outputs = listOf(1)))
                `fails with`("The issuer must be the signing party when an amount of tokens are issued")
            }
            command(carly.owningKey, IssueTokenCommand(carlyAirMile, outputs = listOf(1)))
            verifies()

        }
    }

    @Test
    fun `Can have different issuers in Issue transaction`() {
        ledgerServices.transaction {
            attachment("com.r3.corda.lib.tokens.contracts.FungibleTokenContract")
            output(FungibleTokenContract.contractId, FungibleToken(10 of aliceAirMile, bob))
            output(FungibleTokenContract.contractId, FungibleToken(20 of aliceAirMile, alice))
            output(FungibleTokenContract.contractId, FungibleToken(30 of aliceAirMile, bob))
            output(FungibleTokenContract.contractId, FungibleToken(20 of carlyAirMile, bob))
            output(FungibleTokenContract.contractId, FungibleToken(20 of carlyAirMile, alice))
            command(alice.owningKey, IssueTokenCommand(aliceAirMile, outputs = listOf(0, 1, 2)))
            command(carly.owningKey, IssueTokenCommand(carlyAirMile, outputs = listOf(3, 4)))
            verifies()
        }
    }

}