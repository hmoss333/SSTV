package com.sstv.codec;

import java.util.Collections;
import java.util.List;

/**
 * Describes one SSTV mode: resolution, VIS code, and the repeating per-row
 * sequence of sync/tone/channel segments that make up its signal.
 * <p>
 * Modeling a row as an ordered segment list (rather than hardcoding "sync
 * comes first") is what lets this app support modes whose sync pulse sits
 * in the middle of the row, like Scottie's -- see {@link SSTVModes}.
 */
public final class SSTVMode {
    public final String name;
    public final int visCode;
    public final int width;
    public final int height;
    public final List<Segment> rowTemplate;

    /**
     * Extra standalone sync pulse some modes (e.g. Scottie) send right after the
     * VIS header, before the first row, purely so a receiver can lock before any
     * color data starts. 0 if the mode's own row-sync is enough (e.g. Martin,
     * whose row template already starts with its sync pulse).
     */
    public final double leadingSyncMs;

    public SSTVMode(String name, int visCode, int width, int height,
                     List<Segment> rowTemplate, double leadingSyncMs) {
        this.name = name;
        this.visCode = visCode;
        this.width = width;
        this.height = height;
        this.rowTemplate = Collections.unmodifiableList(rowTemplate);
        this.leadingSyncMs = leadingSyncMs;

        boolean foundSync = false;
        for (Segment s : rowTemplate) {
            if (s.kind == Segment.Kind.SYNC) {
                if (foundSync) {
                    throw new IllegalArgumentException(name + ": more than one SYNC segment in row template");
                }
                foundSync = true;
            }
        }
        if (!foundSync) {
            throw new IllegalArgumentException(name + ": row template has no SYNC segment");
        }
    }

    public double rowDurationMs() {
        double total = 0;
        for (Segment s : rowTemplate) total += s.ms;
        return total;
    }

    /** Milliseconds from the start of the row template to the start of its SYNC segment. */
    public double syncOffsetMs() {
        double offset = 0;
        for (Segment s : rowTemplate) {
            if (s.kind == Segment.Kind.SYNC) return offset;
            offset += s.ms;
        }
        throw new IllegalStateException("unreachable: constructor guarantees a SYNC segment");
    }

    public double syncMs() {
        for (Segment s : rowTemplate) {
            if (s.kind == Segment.Kind.SYNC) return s.ms;
        }
        throw new IllegalStateException("unreachable: constructor guarantees a SYNC segment");
    }

    @Override
    public String toString() {
        return name;
    }
}
