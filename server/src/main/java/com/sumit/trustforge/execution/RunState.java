package com.sumit.trustforge.execution;
import java.util.Set;

public enum RunState {
    CREATED,QUEUED,RUNNING,COMPLETED,FAILED,CANCELLED,TIMED_OUT;
    public boolean terminal() {return Set.of(COMPLETED,FAILED,CANCELLED,TIMED_OUT).contains(this);}
    public boolean canTransitionTo(RunState next) {
        return switch(this) {
            case CREATED -> next==QUEUED || next==FAILED;
            case QUEUED -> next==RUNNING || next==CANCELLED || next==FAILED;
            case RUNNING -> Set.of(COMPLETED,FAILED,CANCELLED,TIMED_OUT).contains(next);
            default -> false;
        };
    }
}
