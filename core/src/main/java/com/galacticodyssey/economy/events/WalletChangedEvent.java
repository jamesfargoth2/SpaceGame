package com.galacticodyssey.economy.events;

public final class WalletChangedEvent {
    public final int playerEntityId;
    public final long newBalance;

    public WalletChangedEvent(int playerEntityId, long newBalance) {
        this.playerEntityId = playerEntityId;
        this.newBalance = newBalance;
    }
}
