package se.laz.casual.standalone.outbound;

import se.laz.casual.api.buffer.CasualBuffer;
import se.laz.casual.api.buffer.ServiceReturn;
import se.laz.casual.api.buffer.type.ServiceBuffer;
import se.laz.casual.api.flags.AtmiFlags;
import se.laz.casual.api.flags.ErrorState;
import se.laz.casual.api.flags.Flag;
import se.laz.casual.api.flags.ServiceReturnState;
import se.laz.casual.api.network.protocol.messages.CasualNWMessage;
import se.laz.casual.api.service.ServiceDetails;
import se.laz.casual.api.util.PrettyPrinter;
import se.laz.casual.config.ConfigurationService;
import se.laz.casual.config.Domain;
import se.laz.casual.network.connection.CasualConnectionException;
import se.laz.casual.network.protocol.messages.CasualNWMessageImpl;
import se.laz.casual.network.protocol.messages.domain.CasualDomainDiscoveryReplyMessage;
import se.laz.casual.network.protocol.messages.domain.CasualDomainDiscoveryRequestMessage;
import se.laz.casual.network.protocol.messages.domain.Service;
import se.laz.casual.network.protocol.messages.service.CasualServiceCallReplyMessage;
import se.laz.casual.network.protocol.messages.service.CasualServiceCallRequestMessage;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class ServiceCallerImpl implements ServiceCaller
{
    private static final Logger LOG = Logger.getLogger(ServiceCallerImpl.class.getName());
    private static final String SERVICE_NAME_LITERAL = " serviceName: ";

    private final CasualConnection connection;

    private ServiceCallerImpl(CasualConnection connection)
    {
        this.connection = connection;
    }

    public static ServiceCaller of(CasualConnection connection)
    {
        Objects.requireNonNull(connection, "networkConnection can not be null");
        return new ServiceCallerImpl(connection);
    }

    @Override
    public ServiceReturn<CasualBuffer> tpcall(String serviceName, CasualBuffer data, Flag<AtmiFlags> flags)
    {
        try
        {
            return tpacall(serviceName, data, flags).join();
        }
        catch (Exception e)
        {
            throw new CasualConnectionException(e);
        }
    }

    @Override
    public CompletableFuture<ServiceReturn<CasualBuffer>> tpacall(String serviceName, CasualBuffer data, Flag<AtmiFlags> flags)
    {
        CompletableFuture<ServiceReturn<CasualBuffer>> f = new CompletableFuture<>();
        UUID corrId = UUID.randomUUID();
        CompletableFuture<CasualNWMessage<CasualServiceCallReplyMessage>> ff = makeServiceCall(corrId, serviceName, data, flags);
        ff.whenComplete((v, e) ->
        {
            if (null != e)
            {
                LOG.finest(() -> "service call request failed for corrid: " + PrettyPrinter.casualStringify(corrId) + SERVICE_NAME_LITERAL + serviceName);
                f.completeExceptionally(e);
                return;
            }
            LOG.finest(() -> "service call request ok for corrid: " + PrettyPrinter.casualStringify(corrId) + SERVICE_NAME_LITERAL + serviceName);
            f.complete(toServiceReturn(v));
        });
        return f;
    }

    @Override
    public boolean serviceExists(String serviceName)
    {
        CasualNWMessage<CasualDomainDiscoveryReplyMessage> replyMsg = serviceDiscovery(UUID.randomUUID(), serviceName);
        return replyMsg.getMessage().getServices().stream()
                       .map(Service::getName)
                       .anyMatch(v -> v.equals(serviceName));
    }

    @Override
    public List<ServiceDetails> serviceDetails(String serviceName)
    {
        List<ServiceDetails> serviceDetailsList = new ArrayList<>();
        CasualNWMessage<CasualDomainDiscoveryReplyMessage> replyMsg = serviceDiscovery(UUID.randomUUID(), serviceName);
        replyMsg
                .getMessage()
                .getServices()
                .forEach(service -> serviceDetailsList.add(
                        ServiceDetails.createBuilder()
                                      .withName(service.getName())
                                      .withCategory(service.getCategory())
                                      .withTransactionType(service.getTransactionType())
                                      .withTimeout(service.getTimeout())
                                      .withHops(service.getHops()).build()));

        return serviceDetailsList;
    }

    private CompletableFuture<CasualNWMessage<CasualServiceCallReplyMessage>> makeServiceCall(UUID corrid, String serviceName, CasualBuffer data, Flag<AtmiFlags> flags)
    {
        Duration timeout = Duration.of(connection.getTransactionTimeout(), ChronoUnit.SECONDS);
        CasualServiceCallRequestMessage serviceRequestMessage = CasualServiceCallRequestMessage.createBuilder()
                                                                                               .setExecution(UUID.randomUUID())
                                                                                               .setServiceBuffer(ServiceBuffer.of(data))
                                                                                               .setServiceName(serviceName)
                                                                                               .setXid(connection.getCurrentXid())
                                                                                               .setTimeout(timeout.toNanos())
                                                                                               .setXatmiFlags(flags).build();
        CasualNWMessage<CasualServiceCallRequestMessage> serviceRequestNetworkMessage = CasualNWMessageImpl.of(corrid, serviceRequestMessage);
        LOG.finest(() -> "issuing service call reequest, corrid: " + PrettyPrinter.casualStringify(corrid) + SERVICE_NAME_LITERAL + serviceName);
        return connection.getNetworkConnection().request(serviceRequestNetworkMessage);
    }

    private CasualNWMessage<CasualDomainDiscoveryReplyMessage> serviceDiscovery(UUID corrid, String serviceName)
    {
        LOG.finest(() -> "issuing domain discovery, corrid: " + PrettyPrinter.casualStringify(corrid) + SERVICE_NAME_LITERAL + serviceName);
        Domain domain = ConfigurationService.getInstance().getConfiguration().getDomain();
        CasualDomainDiscoveryRequestMessage requestMsg = CasualDomainDiscoveryRequestMessage.createBuilder()
                                                                                            .setExecution(UUID.randomUUID())
                                                                                            .setDomainId(domain.getId())
                                                                                            .setDomainName(domain.getName())
                                                                                            .setServiceNames(Arrays.asList(serviceName))
                                                                                            .build();
        CasualNWMessage<CasualDomainDiscoveryRequestMessage> msg = CasualNWMessageImpl.of(corrid, requestMsg);
        CompletableFuture<CasualNWMessage<CasualDomainDiscoveryReplyMessage>> replyMsgFuture = connection.getNetworkConnection().request(msg);

        CasualNWMessage<CasualDomainDiscoveryReplyMessage> replyMsg = replyMsgFuture.join();
        LOG.finest(() -> "domain discovery ok for corrid: " + PrettyPrinter.casualStringify(corrid) + SERVICE_NAME_LITERAL + serviceName);
        return replyMsg;
    }

    private ServiceReturn<CasualBuffer> toServiceReturn(CasualNWMessage<CasualServiceCallReplyMessage> v)
    {
        CasualServiceCallReplyMessage serviceReplyMessage = v.getMessage();
        return new ServiceReturn<>(serviceReplyMessage.getServiceBuffer(), (serviceReplyMessage.getError() == ErrorState.OK) ? ServiceReturnState.TPSUCCESS : ServiceReturnState.TPFAIL, serviceReplyMessage.getError(), serviceReplyMessage.getUserDefinedCode());
    }
}
