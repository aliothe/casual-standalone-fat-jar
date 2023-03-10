/*
 * Copyright (c) 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.standalone.outbound;

import se.laz.casual.api.flags.ErrorState;
import se.laz.casual.api.network.protocol.messages.CasualNWMessage;
import se.laz.casual.api.queue.DequeueReturn;
import se.laz.casual.api.queue.EnqueueReturn;
import se.laz.casual.api.queue.MessageSelector;
import se.laz.casual.api.queue.QueueInfo;
import se.laz.casual.api.queue.QueueMessage;
import se.laz.casual.config.ConfigurationService;
import se.laz.casual.config.Domain;
import se.laz.casual.network.connection.CasualConnectionException;
import se.laz.casual.network.protocol.messages.CasualNWMessageImpl;
import se.laz.casual.network.protocol.messages.domain.CasualDomainDiscoveryReplyMessage;
import se.laz.casual.network.protocol.messages.domain.CasualDomainDiscoveryRequestMessage;
import se.laz.casual.network.protocol.messages.domain.Queue;
import se.laz.casual.network.protocol.messages.queue.CasualDequeueReplyMessage;
import se.laz.casual.network.protocol.messages.queue.CasualDequeueRequestMessage;
import se.laz.casual.network.protocol.messages.queue.CasualEnqueueReplyMessage;
import se.laz.casual.network.protocol.messages.queue.CasualEnqueueRequestMessage;
import se.laz.casual.network.protocol.messages.queue.EnqueueMessage;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class QueueCallerImpl implements QueueCaller
{
    private static final Logger LOG = Logger.getLogger(QueueCallerImpl.class.getName());
    private final CasualConnection connection;

    private QueueCallerImpl(CasualConnection connection)
    {
        this.connection = connection;
    }

    public static QueueCaller of(CasualConnection casualConnection)
    {
        Objects.requireNonNull(casualConnection, "casualConnection can not be null");
        return new QueueCallerImpl(casualConnection);
    }

    @Override
    public EnqueueReturn enqueue(QueueInfo qinfo, QueueMessage msg)
    {
        try
        {
            // Always setting error state OK for now. In the future when error state is handled in the casual queue
            // protocol any error state supplied from casual should be used (same with dequeue)
            return EnqueueReturn.createBuilder().withErrorState(ErrorState.OK).withId(makeEnqueueCall(UUID.randomUUID(), qinfo, msg)).build();
        }
        catch(Exception e)
        {
            throw new CasualConnectionException(e);
        }
    }



    @Override
    public DequeueReturn dequeue(QueueInfo qinfo, MessageSelector selector)
    {
        try
        {
            // Always setting error state OK for now. In the future when error state is handled in the casual queue
            // protocol any error state supplied from casual should be used (same with enqueue)
            return DequeueReturn.createBuilder().withErrorState(ErrorState.OK).withQueueMessage(makeDequeueCall(UUID.randomUUID(), qinfo, selector)).build();
        }
        catch(Exception e)
        {
            throw new CasualConnectionException(e);
        }
    }

    @Override
    public boolean queueExists(QueueInfo qinfo)
    {
        try
        {
            return queueExists(UUID.randomUUID(), qinfo.getQueueName());
        }
        catch(Exception e)
        {
            throw new CasualConnectionException(e);
        }
    }

    private UUID makeEnqueueCall(UUID corrid, QueueInfo qinfo, QueueMessage msg)
    {
        CasualEnqueueRequestMessage requestMessage = CasualEnqueueRequestMessage.createBuilder()
                                                                                .withExecution(UUID.randomUUID())
                                                                                .withXid(connection.getCurrentXid())
                                                                                .withQueueName(qinfo.getQueueName())
                                                                                .withMessage(EnqueueMessage.of(msg))
                                                                                .build();
        CasualNWMessage<CasualEnqueueRequestMessage> networkRequestMessage = CasualNWMessageImpl.of(corrid, requestMessage);
        CompletableFuture<CasualNWMessage<CasualEnqueueReplyMessage>> networkReplyMessageFuture = connection.getNetworkConnection().request(networkRequestMessage);

        CasualNWMessage<CasualEnqueueReplyMessage> networkReplyMessage = networkReplyMessageFuture.join();
        CasualEnqueueReplyMessage replyMessage = networkReplyMessage.getMessage();
        return replyMessage.getId();
    }

    private QueueMessage makeDequeueCall(UUID corrid, QueueInfo qinfo, MessageSelector selector)
    {
        CasualDequeueRequestMessage requestMessage = CasualDequeueRequestMessage.createBuilder()
                                                                                .withExecution(UUID.randomUUID())
                                                                                .withXid(connection.getCurrentXid())
                                                                                .withQueueName(qinfo.getQueueName())
                                                                                .withSelectorProperties(selector.getSelector())
                                                                                .withSelectorUUID(selector.getSelectorId())
                                                                                .withBlock(qinfo.getOptions().isBlocking())
                                                                                .build();
        CasualNWMessage<CasualDequeueRequestMessage> networkRequestMessage = CasualNWMessageImpl.of(corrid, requestMessage);
        CompletableFuture<CasualNWMessage<CasualDequeueReplyMessage>> networkReplyMessageFuture = connection.getNetworkConnection().request(networkRequestMessage);

        CasualNWMessage<CasualDequeueReplyMessage> networkReplyMessage = networkReplyMessageFuture.join();
        CasualDequeueReplyMessage replyMessage = networkReplyMessage.getMessage();
        LOG.info(() -> "replyMessages?" + !replyMessage.getMessages().isEmpty());
        if(!replyMessage.getMessages().isEmpty())
        {
            LOG.info(() -> "payload: " + new String(replyMessage.getMessages().get(0).getPayload().getPayload().get(0)));
        }
        List<QueueMessage> messages = Transformer.transform(replyMessage.getMessages());
        return messages.isEmpty() ? null : messages.get(0);
    }

    private boolean queueExists(UUID corrid, String queueName)
    {
        Domain domain = ConfigurationService.getInstance().getConfiguration().getDomain();
        CasualDomainDiscoveryRequestMessage requestMsg = CasualDomainDiscoveryRequestMessage.createBuilder()
                                                                                            .setExecution(UUID.randomUUID())
                                                                                            .setDomainId(domain.getId())
                                                                                            .setDomainName(domain.getName())
                                                                                            .setQueueNames(Arrays.asList(queueName))
                                                                                            .build();
        CasualNWMessage<CasualDomainDiscoveryRequestMessage> msg = CasualNWMessageImpl.of(corrid, requestMsg);
        CompletableFuture<CasualNWMessage<CasualDomainDiscoveryReplyMessage>> replyMsgFuture = connection.getNetworkConnection().request(msg);

        CasualNWMessage<CasualDomainDiscoveryReplyMessage> replyMsg = replyMsgFuture.join();
        return replyMsg.getMessage().getQueues().stream()
                       .map(Queue::getName)
                       .anyMatch(v -> v.equals(queueName));
    }
}
