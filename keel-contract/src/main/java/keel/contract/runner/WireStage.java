package keel.contract.runner;

/** Wire form of the durable claim stage. Names must match DurableJobClaim.Stage 1:1. */
public enum WireStage { ADMITTED, BROWSER, EMIT, PUBLISH }
