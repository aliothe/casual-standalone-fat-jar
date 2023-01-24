package se.laz.casual.standalone.outbound;

import java.util.Optional;

public interface CasualManagedConnection
{
    Optional<Caller> getCaller();
    void close();
}
