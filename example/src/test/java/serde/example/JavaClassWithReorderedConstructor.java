package serde.example;

import serde.annotation.Serde;

/**
 * Java class where constructor parameter order differs from property order.
 * Constructor: (parentA, parentB, childProp) — must be used when reconstructing.
 * KSP getAllProperties() may return childProp first (own), then parent props.
 */
@Serde
public class JavaClassWithReorderedConstructor extends ParentWithTwoParams {

    private final String childProp;

    public JavaClassWithReorderedConstructor(String parentA, String parentB, String childProp) {
        super(parentA, parentB);
        this.childProp = childProp;
    }

    public String getChildProp() {
        return childProp;
    }
}
