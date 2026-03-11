package serde.example;

import serde.annotation.Mutable;
import serde.annotation.PropertyName;
import serde.annotation.Serde;

/**
 * Java bean with nullable String — getters don't trigger Kotlin smart-cast,
 * so generated write must use !! for compiler.
 */
@Serde
@Mutable
public class NullableFieldsBean {
    private String required;
    private String optional;
    private String secret;

    public String getRequired() { return required; }
    public void setRequired(String v) { required = v; }

    public String getOptional() { return optional; }
    public void setOptional(String v) { optional = v; }

    @PropertyName(bson = "secret_bson", json = "secret")
    public String getSecret() { return secret; }
    public void setSecret(String v) { secret = v; }
}
