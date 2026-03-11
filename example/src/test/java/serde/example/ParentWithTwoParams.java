package serde.example;

import serde.annotation.Serde;

@Serde
public class ParentWithTwoParams {

    protected final String parentA;
    protected final String parentB;

    public ParentWithTwoParams(String parentA, String parentB) {
        this.parentA = parentA;
        this.parentB = parentB;
    }

    public String getParentA() {
        return parentA;
    }

    public String getParentB() {
        return parentB;
    }
}