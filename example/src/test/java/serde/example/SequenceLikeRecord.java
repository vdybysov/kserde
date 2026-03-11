package serde.example;

import serde.annotation.Serde;

import java.util.Map;

/**
 * Mimics Sequence from socialsend backend:
 * - Java record with @Serde on record component
 * - Implements interface
 * Note: Explicit canonical constructor causes KSP to lose record component annotations.
 * Use compact form when possible.
 */
@Serde
public record SequenceLikeRecord(
        @Serde(with = serde.example.CustomMapSerde.class) Map<String, String> steps
) implements java.io.Serializable {}
