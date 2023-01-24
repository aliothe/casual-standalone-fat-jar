package se.laz.casual.standalone.outbound;

import se.laz.casual.network.ProtocolVersion;
import se.laz.casual.network.outbound.NetworkListener;

import javax.transaction.TransactionManager;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;

public class CallerProducerImpl implements CallerProducer
{
    private static final String DOMAIN_NAME = getDomainName();
    private static final int RESOURCE_MANAGER_ID = 42;

    private CallerProducerImpl()
    {}

    public static CallerProducerImpl of()
    {
        return new CallerProducerImpl();
    }

    public Caller createCaller(TransactionManager transactionManager, String host, int port, NetworkListener networkListener)
    {
        InetSocketAddress address = new InetSocketAddress(host, port);
        return CallerImpl.createBuilder()
                         .withAddress(address)
                         .withDomainId(UUID.randomUUID())
                         .withDomainName(DOMAIN_NAME)
                         .withNetworkListener(networkListener)
                         .withProtocolVersion(ProtocolVersion.VERSION_1_0)
                         .withResourceManagerId(RESOURCE_MANAGER_ID)
                         .withTransactionManager(transactionManager)
                         .build();
    }

    private static String getDomainName()
    {
        return Optional.ofNullable(System.getenv("DOMAIN_NAME")).orElseGet(() -> "JAVA-QUARKUS_TEST_APP-" + UUID.randomUUID());
    }
}
