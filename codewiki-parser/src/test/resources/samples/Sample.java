package com.example;

public class Calculator {
    private int value;

    public Calculator(int initial) {
        this.value = initial;
    }

    public int add(int x) {
        return value + x;
    }

    public int getValue() {
        return value;
    }

    enum Operation { ADD, SUBTRACT, MULTIPLY }

    interface Resettable {
        void reset();
    }
}
