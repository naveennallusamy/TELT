package com.telt.entity.exam;

/**
 * What a tenant admin may change, per schedule, on a distributed platform exam. The schedule window and time zone
 * can always be changed; questions and marks never can. Every flag defaults to off.
 */
public record OverridePolicy(Boolean duration, Boolean maxAttempts, Boolean resultRelease, Boolean shuffle,
                             Boolean instructions, Boolean lateEntry) {

    public static final OverridePolicy ALL = new OverridePolicy(true, true, true, true, true, true);

    public OverridePolicy normalized() {
        return new OverridePolicy(Boolean.TRUE.equals(duration), Boolean.TRUE.equals(maxAttempts),
                Boolean.TRUE.equals(resultRelease), Boolean.TRUE.equals(shuffle), Boolean.TRUE.equals(instructions),
                Boolean.TRUE.equals(lateEntry));
    }
}
