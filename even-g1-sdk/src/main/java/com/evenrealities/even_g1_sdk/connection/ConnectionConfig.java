package com.evenrealities.even_g1_sdk.connection;

import java.util.UUID;

public class ConnectionConfig {
    public final UUID uartServiceUuid;
    public final UUID uartTxCharUuid;
    public final UUID uartRxCharUuid;
    public final UUID clientCharacteristicConfigUuid;
    public final int mtu;

    public ConnectionConfig(UUID uartServiceUuid, UUID uartTxCharUuid, UUID uartRxCharUuid, UUID clientCharacteristicConfigUuid, int mtu) {
        this.uartServiceUuid = uartServiceUuid;
        this.uartTxCharUuid = uartTxCharUuid;
        this.uartRxCharUuid = uartRxCharUuid;
        this.clientCharacteristicConfigUuid = clientCharacteristicConfigUuid;
        this.mtu = mtu;
    }
}