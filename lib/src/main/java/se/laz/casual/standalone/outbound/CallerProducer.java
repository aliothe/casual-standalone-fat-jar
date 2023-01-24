package se.laz.casual.standalone.outbound;

import se.laz.casual.network.outbound.NetworkListener;

import javax.transaction.TransactionManager;

public interface CallerProducer
{
    Caller createCaller(TransactionManager transactionManager, String host, int port, NetworkListener networkListener);
}
