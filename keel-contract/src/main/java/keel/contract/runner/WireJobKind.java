package keel.contract.runner;

/** Wire form of the job kind. Names must match the portal's JobRecord.JobKind 1:1. */
public enum WireJobKind { CONVERT, EXECUTE, GENERATE_BATCH, GENERATE_COMPARE, HUNT }
