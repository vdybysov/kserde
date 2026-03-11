package serde.example;

import serde.annotation.Serde;

import java.util.Map;

/**
 * Java record with @Serde on Map record component.
 * Same pattern as Sequence(steps) in socialsend backend.
 */
@Serde
public record JavaRecordWithMapSerde(
        @Serde(with = serde.example.CustomMapSerde.class) Map<String, String> steps
) {
}
