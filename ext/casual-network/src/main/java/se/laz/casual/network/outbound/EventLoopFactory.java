/*
 * Copyright (c) 2021, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.network.outbound;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import se.laz.casual.config.ConfigurationService;
import se.laz.casual.config.Outbound;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class EventLoopFactory
{
    private static EventLoopGroup instance;
    private EventLoopFactory()
    {}
    public synchronized EventLoopGroup getInstance()
    {
        if(null == instance)
        {
            instance = createEventLoopGroup();
        }
        return instance;
    }

    private EventLoopGroup createEventLoopGroup()
    {
        Outbound outbound = ConfigurationService.getInstance().getConfiguration().getOutbound();
        return new NioEventLoopGroup(outbound.getNumberOfThreads());
    }

    public static ExecutorService getExecutorService()
    {
        return Executors.newFixedThreadPool(Math.max(1, Runtime.getRuntime().availableProcessors() * 2));
    }

    public static EventLoopFactory of()
    {
        return new EventLoopFactory();
    }
}
