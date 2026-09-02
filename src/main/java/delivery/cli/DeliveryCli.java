package delivery.cli;

import delivery.excel.InvalidExcelTemplateException;
import delivery.job.ConversionJobRequest;
import delivery.job.ConversionJobResult;
import delivery.job.ConversionJobRunner;
import org.json.JSONObject;

import java.nio.file.Path;

public final class DeliveryCli {
    private DeliveryCli() {
    }

    public static void main(String[] args) {
        try {
            Args parsed = Args.parse(args);
            utils.PropertyReader.loadProperties();
            delivery.heal.LocalApiKeyLoader.loadFromWorkingTree();
            ConversionJobRequest request = new ConversionJobRequest(
                    parsed.projectId,
                    parsed.excel,
                    parsed.baseUrl,
                    parsed.username,
                    parsed.password,
                    parsed.workDir,
                    parsed.storeRoot,
                    parsed.templateRoot,
                    parsed.mode,
                    parsed.llmBaseUrl,
                    parsed.llmModel,
                    parsed.finalRevise
            );
            ConversionJobResult result = new ConversionJobRunner().run(request);
            JSONObject out = new JSONObject();
            out.put("status", result.jobStatus() == null ? "COMPLETED" : result.jobStatus());
            out.put("reviseVerdict", result.reviseVerdict() == null ? "" : result.reviseVerdict());
            out.put("zipPath", result.zipFile().toAbsolutePath().toString());
            out.put("passedCount", result.passed());
            out.put("todoCount", result.todo());
            out.put("scoreReportPath", result.scoreReport() == null ? "" : result.scoreReport().toAbsolutePath().toString());
            out.put("message", result.message() == null ? "" : result.message());
            System.out.println(out);
            System.exit(result.softBlocked() ? 5 : 0);
        } catch (InvalidExcelTemplateException e) {
            System.err.println(new JSONObject()
                    .put("error", e.getErrorCode())
                    .put("message", e.getMessage()));
            System.exit(2);
        } catch (IllegalStateException e) {
            if ("UPDATE_WITHOUT_FRAMEWORK".equals(e.getMessage())) {
                System.err.println(new JSONObject()
                        .put("error", "UPDATE_WITHOUT_FRAMEWORK")
                        .put("message", "Run NEW before UPDATE"));
                System.exit(3);
            }
            System.err.println(new JSONObject().put("error", "RUNTIME").put("message", e.getMessage()));
            System.exit(4);
        } catch (Exception e) {
            System.err.println(new JSONObject()
                    .put("error", "RUNTIME")
                    .put("message", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            System.exit(4);
        }
    }

    private record Args(
            String projectId,
            Path excel,
            String baseUrl,
            String username,
            String password,
            Path workDir,
            Path storeRoot,
            Path templateRoot,
            String mode,
            String llmBaseUrl,
            String llmModel,
            boolean finalRevise
    ) {
        static Args parse(String[] args) {
            String projectId = null;
            Path excel = null;
            String baseUrl = null;
            String username = "";
            String password = "";
            Path workDir = Path.of("./delivery-work");
            Path storeRoot = Path.of("./delivery-store");
            Path templateRoot = Path.of("./customer-framework-template");
            String mode = "NEW";
            String llmBaseUrl = "http://127.0.0.1:11434";
            String llmModel = "gemma4:e2b";
            boolean finalRevise = false;
            for (int i = 0; i < args.length; i++) {
                switch (args[i]) {
                    case "--project-id" -> projectId = args[++i];
                    case "--excel" -> excel = Path.of(args[++i]);
                    case "--base-url" -> baseUrl = args[++i];
                    case "--username" -> username = args[++i];
                    case "--password" -> password = args[++i];
                    case "--work-dir" -> workDir = Path.of(args[++i]);
                    case "--store-root" -> storeRoot = Path.of(args[++i]);
                    case "--template-root" -> templateRoot = Path.of(args[++i]);
                    case "--mode" -> mode = args[++i];
                    case "--llm-base-url" -> llmBaseUrl = args[++i];
                    case "--llm-model" -> llmModel = args[++i];
                    case "--final-revise" -> finalRevise = true;
                    default -> throw new IllegalArgumentException("Unknown arg: " + args[i]);
                }
            }
            if (excel == null || baseUrl == null) {
                throw new IllegalArgumentException("Required: --excel --base-url [--project-id]");
            }
            if (projectId == null || projectId.isBlank()) {
                projectId = delivery.util.ProjectNaming.hostSlug(baseUrl);
            }
            return new Args(projectId, excel, baseUrl, username, password, workDir, storeRoot, templateRoot, mode, llmBaseUrl, llmModel, finalRevise);
        }
    }
}
