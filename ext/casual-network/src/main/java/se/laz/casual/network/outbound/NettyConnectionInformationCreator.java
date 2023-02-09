package se.laz.casual.network.outbound;

import se.laz.casual.config.ConfigurationService;
import se.laz.casual.config.Domain;
import se.laz.casual.network.ProtocolVersion;

import java.net.InetSocketAddress;

public final class NettyConnectionInformationCreator
{
    private NettyConnectionInformationCreator()
    {}

    public static NettyConnectionInformation create(InetSocketAddress address, ProtocolVersion protocolVersion)
    {
        Domain domain = ConfigurationService.getInstance().getConfiguration().getDomain();
        NettyConnectionInformation.Builder builder = NettyConnectionInformation.createBuilder().withAddress(address)
                                                                  .withProtocolVersion(protocolVersion)
                                                                  .withDomainId(domain.getId())
                                                                  .withDomainName(domain.getName());
        return builder.build();
    }
}
