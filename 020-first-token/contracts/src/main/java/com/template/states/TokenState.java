package com.template.states;

import com.template.contracts.TokenContract;
import net.corda.core.contracts.BelongsToContract;
import net.corda.core.contracts.ContractState;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@BelongsToContract(TokenContract.class)
public final class TokenState implements ContractState {

    @NotNull
    private final Party issuer;
    @NotNull
    private final Party holder;
    private final long quantity;

    public TokenState(@NotNull final Party issuer, @NotNull final Party holder, final long quantity) {
        //noinspection ConstantConditions
        if (issuer == null) throw new NullPointerException("issuer cannot be null");
        //noinspection ConstantConditions
        if (holder == null) throw new NullPointerException("holder cannot be null");
        this.issuer = issuer;
        this.holder = holder;
        this.quantity = quantity;
    }

    @NotNull
    @Override
    public List<AbstractParty> getParticipants() {
        return Collections.singletonList(holder);
    }

    @NotNull
    public Party getIssuer() {
        return issuer;
    }

    @NotNull
    public Party getHolder() {
        return holder;
    }

    public long getQuantity() {
        return quantity;
    }

    // equals, hashcode and toString are not strictly necessary, although they come in handy in our unit tests.

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final TokenState that = (TokenState) o;
        return quantity == that.quantity &&
                issuer.equals(that.issuer) &&
                holder.equals(that.holder);
    }

    @Override
    public int hashCode() {
        return Objects.hash(issuer, holder, quantity);
    }

    @Override
    public String toString() {
        return "TokenState{" +
                "issuer=" + issuer +
                ", holder=" + holder +
                ", quantity=" + quantity +
                '}';
    }
}