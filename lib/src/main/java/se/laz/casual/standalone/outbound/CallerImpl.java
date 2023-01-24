package se.laz.casual.standalone.outbound;

import se.laz.casual.api.buffer.CasualBuffer;
import se.laz.casual.api.buffer.ServiceReturn;
import se.laz.casual.api.flags.AtmiFlags;
import se.laz.casual.api.flags.ErrorState;
import se.laz.casual.api.flags.Flag;
import se.laz.casual.api.flags.ServiceReturnState;
import se.laz.casual.api.queue.DequeueReturn;
import se.laz.casual.api.queue.EnqueueReturn;
import se.laz.casual.api.queue.MessageSelector;
import se.laz.casual.api.queue.QueueInfo;
import se.laz.casual.api.queue.QueueMessage;
import se.laz.casual.api.service.ServiceDetails;
import se.laz.casual.network.ProtocolVersion;
import se.laz.casual.network.api.NetworkConnection;
import se.laz.casual.network.outbound.CorrelatorImpl;
import se.laz.casual.network.outbound.NettyConnectionInformation;
import se.laz.casual.network.outbound.NettyNetworkConnection;
import se.laz.casual.network.outbound.NetworkListener;
import se.laz.casual.standalone.CasualXAResource;
import se.laz.casual.standalone.TransactionWrapper;

import javax.transaction.TransactionManager;
import javax.transaction.xa.XAResource;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class CallerImpl implements Caller
{
    private static final Logger LOG = Logger.getLogger(CallerImpl.class.getName());
    private final CasualConnection casualConnection;
    private final ServiceCaller serviceCaller;
    private final QueueCaller queueCaller;
    private final Set<String> serviceCache = new HashSet<>();
    private final Set<String> queueCache = new HashSet<>();
    private final NetworkListenerAdapter networkListenerAdapter;
    private TransactionWrapper transactionWrapper;

    private CallerImpl(CasualConnection casualConnection, ServiceCaller serviceCaller, QueueCaller queueCaller, NetworkListenerAdapter networkListenerAdapter, TransactionManager transactionManager)
    {
        this.casualConnection = casualConnection;
        this.serviceCaller = serviceCaller;
        this.queueCaller = queueCaller;
        this.networkListenerAdapter = networkListenerAdapter;
        this.transactionWrapper = TransactionWrapper.of(transactionManager);
    }

    private static Caller of(InetSocketAddress address, ProtocolVersion protocolVersion, UUID domainId, String domainName, NetworkListener networkListener, int resourceManagerId, TransactionManager transactionManager)
    {
        Objects.requireNonNull(address, "address can not be null");
        Objects.requireNonNull(protocolVersion, "protocolVersion can not be null");
        Objects.requireNonNull(domainId, "domainId can not be null");
        Objects.requireNonNull(domainName, "domainName can not be null");
        Objects.requireNonNull(networkListener, "networkListener can not be null");
        NetworkConnection networkConnection = createNetworkConnection(address, protocolVersion, domainId, domainName, networkListener);
        CasualConnection casualConnection = CasualConnection.of(networkConnection);
        CasualXAResource casualXAResource = CasualXAResource.of(casualConnection, resourceManagerId);
        casualConnection.setCasualXAResource(casualXAResource);
        NetworkListenerAdapter proxyNetworkListener = NetworkListenerAdapter.of(networkListener);
        return new CallerImpl(casualConnection, ServiceCallerImpl.of(casualConnection), QueueCallerImpl.of(casualConnection), proxyNetworkListener, transactionManager);
    }

    private static NetworkConnection createNetworkConnection(InetSocketAddress address, ProtocolVersion protocolVersion, UUID domainId, String domainName, NetworkListener networkListener)
    {
        NettyConnectionInformation connectionInformation = NettyConnectionInformation.createBuilder()
                                                                                          .withDomainId(domainId)
                                                                                          .withDomainName(domainName)
                                                                                          .withAddress(address)
                                                                                          .withProtocolVersion(protocolVersion)
                                                                                          .withCorrelator(CorrelatorImpl.of())
                                                                                          .build();
        return NettyNetworkConnection.of(connectionInformation, networkListener);
    }

    @Override
    public EnqueueReturn enqueue(QueueInfo qinfo, QueueMessage msg)
    {
        if(!queueExists(qinfo))
        {
            return EnqueueReturn.createBuilder()
                                .withErrorState(ErrorState.TPENOENT)
                                .build();
        }
        return transactionWrapper.execute(() -> validateReply(queueCaller.enqueue(qinfo, msg)), casualConnection.getCasualXAResource());
    }

    @Override
    public DequeueReturn dequeue(QueueInfo qinfo, MessageSelector selector)
    {
        if(!queueExists(qinfo))
        {
            return DequeueReturn.createBuilder()
                                .withErrorState(ErrorState.TPENOENT)
                                .build();
        }
        return transactionWrapper.execute(() -> validateReply(queueCaller.dequeue(qinfo, selector)), casualConnection.getCasualXAResource());
    }

    @Override
    public boolean queueExists(QueueInfo qinfo)
    {
        if(queueCache.contains(qinfo.getQueueName()))
        {
            return true;
        }
        if(queueCaller.queueExists(qinfo))
        {
            queueCache.add(qinfo.getQueueName());
            return true;
        }
        return false;
    }

    @Override
    public ServiceReturn<CasualBuffer> tpcall(String serviceName, CasualBuffer data, Flag<AtmiFlags> flags)
    {
        if(!serviceExists(serviceName))
        {
            return createTPENOENTReply(serviceName);
        }
        if(flags.isSet(AtmiFlags.TPNOTRAN))
        {
            LOG.finest(() -> "tpcall TPNOTRAN " + serviceName);
            return serviceCaller.tpcall(serviceName, data, flags);
        }
        LOG.finest(() -> "tpcall " + serviceName);
        return transactionWrapper.execute(() -> validateReply(serviceCaller.tpcall(serviceName, data, flags)), casualConnection.getCasualXAResource());
    }



    private ServiceReturn<CasualBuffer> createTPENOENTReply(String serviceName)
    {
        LOG.warning(() -> "TPENOENT for service: " + serviceName);
        return new ServiceReturn<>(null, ServiceReturnState.TPFAIL, ErrorState.TPENOENT, 0);
    }

    @Override
    public CompletableFuture<ServiceReturn<CasualBuffer>> tpacall(String serviceName, CasualBuffer data, Flag<AtmiFlags> flags)
    {
        if(!serviceExists(serviceName))
        {
            CompletableFuture<ServiceReturn<CasualBuffer>> future = new CompletableFuture<>();
            future.complete(createTPENOENTReply(serviceName));
            return future;
        }
        if(flags.isSet(AtmiFlags.TPNOTRAN))
        {
            LOG.finest(() -> "tpacall TPNOTRAN " + serviceName);
            return serviceCaller.tpacall(serviceName, data, flags);
        }
        LOG.finest(() -> "tpacall " + serviceName);
        return transactionWrapper.execute(() -> serviceCaller.tpacall(serviceName, data, flags), casualConnection.getCasualXAResource());
    }

    @Override
    public boolean serviceExists(String serviceName)
    {
        if(serviceCache.contains(serviceName))
        {
            return true;
        }
        if(serviceCaller.serviceExists(serviceName))
        {
            serviceCache.add(serviceName);
            return true;
        }
        return false;
    }

    @Override
    public List<ServiceDetails> serviceDetails(String serviceName)
    {
        return serviceCaller.serviceDetails(serviceName);
    }

    public static Builder createBuilder()
    {
        return new Builder();
    }

    @Override
    public void close()
    {
        casualConnection.close();
    }

    @Override
    public boolean isDisconnected()
    {
        return networkListenerAdapter.isDisconnected();
    }

    @Override
    public XAResource getXAResource()
    {
        return casualConnection.getCasualXAResource();
    }

    public static final class Builder
    {
        private InetSocketAddress address;
        private ProtocolVersion protocolVersion;
        private UUID domainId;
        private String domainName;
        private NetworkListener networkListener;
        private int resourceManagerId;
        private TransactionManager transactionManager;

        private Builder()
        {}

        public Builder withAddress(InetSocketAddress address)
        {
            this.address = address;
            return this;
        }

        public Builder withProtocolVersion(ProtocolVersion protocolVersion)
        {
            this.protocolVersion = protocolVersion;
            return this;
        }

        public Builder withDomainId(UUID domainId)
        {
            this.domainId = domainId;
            return this;
        }

        public Builder withDomainName(String domainName)
        {
            this.domainName = domainName;
            return this;
        }

        public Builder withNetworkListener(NetworkListener networkListener)
        {
            this.networkListener = networkListener;
            return this;
        }

        public Builder withResourceManagerId(int resourceManagerId)
        {
            this.resourceManagerId = resourceManagerId;
            return this;
        }

        public Builder withTransactionManager(TransactionManager transactionManager)
        {
            this.transactionManager = transactionManager;
            return this;
        }

        public Caller build()
        {
            return CallerImpl.of(address, protocolVersion, domainId, domainName,  networkListener, resourceManagerId, transactionManager);
        }
    }

    private EnqueueReturn validateReply(EnqueueReturn answer)
    {
        if(answer.getErrorState() != ErrorState.OK)
        {
            throw new QueueOperationFailedException("enqueue failed: " + answer);
        }
        return answer;
    }

    private DequeueReturn validateReply(DequeueReturn answer)
    {
        if(answer.getErrorState() != ErrorState.OK)
        {
            throw new QueueOperationFailedException("dequeue failed: " + answer);
        }
        return answer;
    }

    private ServiceReturn<CasualBuffer> validateReply(ServiceReturn<CasualBuffer> reply)
    {
        if(reply.getServiceReturnState() == ServiceReturnState.TPSUCCESS)
        {
            return reply;
        }
        throw new ServiceCallFailedException("tpcall failed: " + reply.getErrorState());
    }

    private static class NetworkListenerAdapter implements NetworkListener
    {
        private final NetworkListener networkListener;
        private boolean disconnected = false;
        private NetworkListenerAdapter(NetworkListener networkListener)
        {
            this.networkListener = networkListener;
        }

        public static NetworkListenerAdapter of(NetworkListener networkListener)
        {
            Objects.requireNonNull(networkListener, "networkListener can not be null");
            return new NetworkListenerAdapter(networkListener);
        }

        @Override
        public void disconnected()
        {
            disconnected = false;
            networkListener.disconnected();
        }

        public boolean isDisconnected()
        {
            return disconnected;
        }
    }
}
