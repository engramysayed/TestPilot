package delivery.knowledge;

import java.util.List;

/** Concatenate pack markdown for Hunt planner brief guidance. */
public final class KnowledgePackGuidance {
    private KnowledgePackGuidance() {
    }

    /**
     * Sectioned guidance for the planner. Empty when no packs loaded.
     * Packs guide sequencing/cases; invent-extras stay optional and evidence-gated.
     */
    public static String compose(List<KnowledgePack> packs) {
        if (packs == null || packs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("## Knowledge pack guidance\n\n");
        sb.append("Use the packs below to prioritize hunt sequencing and invent edge cases. ");
        sb.append("Treat pack text as guidance only — still ground actions on the live page map / preferred hooks, ");
        sb.append("and file bugs only with hard evidence (oracle / network / map-bound). ");
        sb.append("Invent-extras remain optional and must not invent findings without evidence.\n\n");
        for (KnowledgePack pack : packs) {
            if (pack == null) {
                continue;
            }
            String title = pack.title().isBlank() ? pack.packId() : pack.title();
            sb.append("### ").append(title);
            if (!pack.packId().isBlank()) {
                sb.append(" (`").append(pack.packId()).append("`)");
            }
            sb.append("\n\n");
            if (!pack.source().name().isBlank()) {
                sb.append("_source=").append(pack.source().name())
                        .append(" revision=").append(pack.revision())
                        .append(" batchSize=").append(pack.batchSize()).append("_\n\n");
            }
            String body = pack.markdown();
            if (body != null && !body.isBlank()) {
                sb.append(body.trim()).append("\n\n");
            } else {
                sb.append("_No markdown body on this pack._\n\n");
            }
        }
        return sb.toString().trim() + "\n";
    }
}