package serde.example;

import serde.annotation.Serde;

import java.util.List;

/**
 * Java record with @Serde on record component.
 * KSP may expose the annotation on param, accessor, or property depending on version.
 * Processor merges all sources so CustomIdsSerde is found regardless.
 */
@Serde
public record JavaRecordWithCustomSerde(
        @Serde(with = serde.example.CustomIdsSerde.class) List<Integer> ids
) {
}
