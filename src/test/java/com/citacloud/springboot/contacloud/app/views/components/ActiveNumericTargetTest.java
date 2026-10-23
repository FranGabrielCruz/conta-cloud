package com.citacloud.springboot.contacloud.app.views.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ActiveNumericTargetTest {
    @Test void firstDigitReplacesAndFollowingDigitsConcatenate() {
        ActiveNumericTarget active = new ActiveNumericTarget(); FakeTarget target = new FakeTarget("1", true);
        active.activate(target);
        assertTrue(active.input("5")); assertEquals("5", target.preview);
        assertTrue(active.input("0")); assertEquals("50", target.preview);
    }

    @Test void backspaceStartsEditingAndRemovesLastDigit() {
        ActiveNumericTarget active = new ActiveNumericTarget(); FakeTarget target = new FakeTarget("125", true);
        active.activate(target); assertTrue(active.input("Backspace")); assertEquals("12", target.preview);
    }

    @Test void decimalIsNormalizedAndOnlyAcceptedOnce() {
        ActiveNumericTarget active = new ActiveNumericTarget(); FakeTarget target = new FakeTarget("9", true);
        active.activate(target); assertTrue(active.input(",")); assertEquals("0.", target.preview);
        assertTrue(active.input("5")); assertEquals("0.5", target.preview);
        assertFalse(active.input(".")); assertEquals("0.5", target.preview);
    }

    @Test void decimalIsRejectedForIntegerTarget() {
        ActiveNumericTarget active = new ActiveNumericTarget(); FakeTarget target = new FakeTarget("1", false);
        active.activate(target); assertFalse(active.input(".")); assertNull(target.preview);
    }

    @Test void inputWithoutTargetDoesNothing() {
        ActiveNumericTarget active = new ActiveNumericTarget();
        assertFalse(active.input("9")); assertFalse(active.hasTarget());
    }

    @Test void invalidTargetStopsReceivingInput() {
        ActiveNumericTarget active = new ActiveNumericTarget(); FakeTarget target = new FakeTarget("1", true);
        active.activate(target); target.valid = false;
        assertFalse(active.input("2")); assertNull(target.preview); assertFalse(active.hasTarget());
    }

    @Test void switchingTargetCommitsPreviousAndRoutesInputToNewTarget() {
        ActiveNumericTarget active = new ActiveNumericTarget();
        FakeTarget first = new FakeTarget("1", true); FakeTarget second = new FakeTarget("2", true);
        active.activate(first); active.input("7"); active.activate(second); active.input("8");
        assertEquals("7", first.committed); assertEquals("8", second.preview);
    }

    @Test void negativeSignIsNeverAccepted() {
        ActiveNumericTarget active = new ActiveNumericTarget(); FakeTarget target = new FakeTarget("1", true);
        active.activate(target); assertFalse(active.input("-")); assertNull(target.preview);
    }

    @Test void emptyBufferRestoresOriginalWhenFinishing() {
        ActiveNumericTarget active = new ActiveNumericTarget(); FakeTarget target = new FakeTarget("5", true);
        active.activate(target); active.input("Backspace"); active.finish();
        assertTrue(target.restored); assertNull(target.committed);
    }

    @Test void finishRemovesTrailingDecimalBeforeCommit() {
        ActiveNumericTarget active = new ActiveNumericTarget(); FakeTarget target = new FakeTarget("1", true);
        active.activate(target); active.input("2"); active.input("."); active.finish();
        assertEquals("2", target.committed);
    }

    @Test void maxDigitsIsEnforced() {
        ActiveNumericTarget active = new ActiveNumericTarget(); FakeTarget target = new FakeTarget("", true, 2);
        active.activate(target); active.input("1"); active.input("2");
        assertFalse(active.input("3")); assertEquals("12", target.preview);
    }

    @Test void clearOwnerInvalidatesOnlyMatchingTarget() {
        ActiveNumericTarget active = new ActiveNumericTarget(); FakeTarget target = new FakeTarget("4", true);
        active.activate(target); active.clearIfOwner("another"); assertTrue(active.hasTarget());
        active.clearIfOwner(target.owner()); assertFalse(active.hasTarget());
    }

    @Test void finishOwnerCommitsMatchingTargetAndKeepsEnteredValue() {
        ActiveNumericTarget active = new ActiveNumericTarget(); FakeTarget target = new FakeTarget("4", true);
        active.activate(target); active.input("8");
        active.finishIfOwner(target.owner());
        assertEquals("8", target.committed); assertFalse(target.restored); assertFalse(active.hasTarget());
    }

    private static final class FakeTarget implements NumericTarget {
        private final Object owner = new Object(); private final String initial; private final boolean decimal;
        private final int maxDigits; private boolean valid = true; private boolean restored;
        private String preview; private String committed;
        private FakeTarget(String initial, boolean decimal) { this(initial, decimal, 12); }
        private FakeTarget(String initial, boolean decimal, int maxDigits) { this.initial=initial;this.decimal=decimal;this.maxDigits=maxDigits; }
        @Override public Object owner(){return owner;} @Override public String currentRawValue(){return initial;}
        @Override public boolean isValid(){return valid;} @Override public boolean decimalAllowed(){return decimal;}
        @Override public int scale(){return decimal?4:0;} @Override public int maxDigits(){return maxDigits;}
        @Override public void preview(String rawValue){preview=rawValue;} @Override public void commit(String rawValue){committed=rawValue;}
        @Override public void restore(){restored=true;}
    }
}
