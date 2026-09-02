# Local Ollama for Delivery conversion

## Do you need `curl …/api/tags` every time?

**No.** That command only checks that Ollama is up and lists installed models.

- Keep the **Ollama app / Windows service** running in the background.
- Run `curl.exe http://127.0.0.1:11434/api/tags` when:
  - first setup,
  - after reboot if jobs fail with connection errors,
  - after changing models.

Portal jobs call Ollama themselves when binder hits **AMBIGUOUS** or bind failure (vision heal). They do not need the curl each run.

## Vision heal

On ambiguity / bind failure, Delivery sends the Excel intent + candidate shortlist + a **PNG screenshot as base64** in Ollama’s `messages[].images` field.

Use a vision-capable model in `application.properties`, e.g.:

```properties
delivery.llm-model=gemma4:e2b
```

If the model rejects images, Delivery falls back to text-only shortlist pick.

## iframe / Shadow DOM

Runtime search tries default content, then open shadow roots, then each iframe. Candidate tables also list `iframe` entries so heal prompts know a context switch may be needed.
