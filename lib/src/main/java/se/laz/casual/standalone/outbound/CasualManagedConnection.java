/*
 * Copyright (c) 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */

package se.laz.casual.standalone.outbound;

import java.util.Optional;

public interface CasualManagedConnection extends AutoCloseable
{
    Optional<Caller> getCaller();
    void close();
}
