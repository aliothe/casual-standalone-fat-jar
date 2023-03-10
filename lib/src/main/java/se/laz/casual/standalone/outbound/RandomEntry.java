/*
 * Copyright (c) 2023, The casual project. All rights reserved.
 *
 * This software is licensed under the MIT license, https://opensource.org/licenses/MIT
 */
package se.laz.casual.standalone.outbound;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public final class RandomEntry
{
    private RandomEntry()
    {}

    public static Optional<Caller> getEntry(List<Caller> entries)
    {
        if(entries.isEmpty())
        {
            return Optional.empty();
        }
        if(entries.size() == 1)
        {
            return Optional.of(entries.get(0));
        }
        return Optional.of(getRandomEntry((entries)));
    }

    public static Caller getRandomEntry(List<Caller> entries)
    {
        final int max = entries.size();
        final int index = getRandomNumber(0, max);
        return entries.get(index);
    }

    /**
     * min - max
     * Min inclusive, max exclusive
     * 0,5 -> possible range [0,1,2,3,4]
     * @param min
     * @param max
     * @return a pseudorandom number within the range [min, max-1]
     */
    // Pseudo random is fine here
    @SuppressWarnings("java:S2245")
    private static int getRandomNumber(int min, int max)
    {
        return ThreadLocalRandom.current().nextInt(min, max);
    }


}
