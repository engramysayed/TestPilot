package keel.contract.runner;

/** What an engine/runner advertises at claim time. Unknown values from newer engines are ignored by the portal. */
public enum RunnerCapability { BROWSER_CHROME, LLM_LOCAL, LLM_CLOUD, VISION, CODEGEN_JAVA, HUNT }
