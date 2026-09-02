package delivery.portal.api;



import delivery.excel.UserStoryBulkParser;

import delivery.portal.model.JobRecord;

import delivery.portal.model.ProjectRecord;

import delivery.portal.security.CurrentUserService;

import delivery.portal.service.PortalStore;

import delivery.portal.service.GenerateModelService;

import delivery.portal.worker.GenerateBatchWorker;

import org.springframework.http.HttpStatus;

import org.springframework.http.MediaType;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.PathVariable;

import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.multipart.MultipartFile;



import java.nio.charset.StandardCharsets;

import java.nio.file.Files;

import java.nio.file.Path;

import java.util.Map;

import java.util.UUID;



@RestController

@RequestMapping("/api/projects")

public class GenerateBatchController {

    private final PortalStore store;

    private final GenerateBatchWorker worker;

    private final CurrentUserService currentUser;

    private final GenerateModelService models;



    public GenerateBatchController(

            PortalStore store,

            GenerateBatchWorker worker,

            CurrentUserService currentUser,

            GenerateModelService models

    ) {

        this.store = store;

        this.worker = worker;

        this.currentUser = currentUser;

        this.models = models;

    }



    @PostMapping(value = "/{projectId}/generate-batch-jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)

    public ResponseEntity<?> createBatchJob(

            @PathVariable("projectId") String projectId,

            @RequestParam("storiesFile") MultipartFile storiesFile,

            @RequestParam(value = "reviewPass", required = false, defaultValue = "false") String reviewPass,

            @RequestParam(value = "model", required = false) String model

    ) throws Exception {

        if (storiesFile == null || storiesFile.isEmpty()) {

            return ResponseEntity.badRequest()

                    .body(new ApiError("BAD_REQUEST", "storiesFile is required").asMap());

        }



        String raw = new String(storiesFile.getBytes(), StandardCharsets.UTF_8);

        try {

            UserStoryBulkParser.parse(raw);

        } catch (IllegalArgumentException e) {

            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)

                    .body(new ApiError("INVALID_BULK_FORMAT", e.getMessage()).asMap());

        }



        boolean wantReview = parseReviewPass(reviewPass);

        return enqueueBatchJob(projectId, raw, wantReview, model);

    }



    @PostMapping(value = "/{projectId}/generate-async", consumes = MediaType.APPLICATION_JSON_VALUE)

    public ResponseEntity<?> createAsyncSingleJob(

            @PathVariable("projectId") String projectId,

            @RequestBody GenerateTcController.GenerateTcRequest body

    ) throws Exception {

        if (body == null || body.stories() == null || body.stories().isBlank()) {

            return ResponseEntity.badRequest()

                    .body(new ApiError("BAD_REQUEST", "stories field is required").asMap());

        }

        boolean wantReview = false;

        String model = null;

        if (body.options() != null) {

            if (body.options().get("reviewPass") instanceof Boolean b) {

                wantReview = b;

            }

            if (body.options().get("model") instanceof String s && !s.isBlank()) {

                model = s.trim();

            }

        }

        String raw;

        try {

            raw = UserStoryBulkParser.toSingleStoryCsv(body.stories());

            UserStoryBulkParser.parse(raw);

        } catch (IllegalArgumentException e) {

            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)

                    .body(new ApiError("INVALID_STORIES", e.getMessage()).asMap());

        }

        return enqueueBatchJob(projectId, raw, wantReview, model);

    }



    private ResponseEntity<?> enqueueBatchJob(

            String projectId,

            String raw,

            boolean wantReview,

            String model

    ) throws Exception {

        Long ownerId = currentUser.requireUserId();

        ProjectRecord project = store.getOwnedProject(projectId, ownerId).orElse(null);

        if (project == null) {

            return ResponseEntity.status(HttpStatus.NOT_FOUND)

                    .body(new ApiError("NOT_FOUND", "Unknown project").asMap());

        }

        if (project.isArchived()) {

            return ResponseEntity.status(HttpStatus.CONFLICT)

                    .body(new ApiError("PROJECT_ARCHIVED", "Cannot start jobs on archived project").asMap());

        }



        Path uploadDir = Path.of(System.getProperty("java.io.tmpdir"), "delivery-uploads", projectId, "generate-batch");

        Files.createDirectories(uploadDir);

        Path storiesPath = uploadDir.resolve(UUID.randomUUID() + "-stories.csv");

        Files.writeString(storiesPath, raw, StandardCharsets.UTF_8);



        String resolvedModel;

        try {

            resolvedModel = models.resolve(model);

        } catch (IllegalArgumentException e) {

            return ResponseEntity.status(HttpStatus.BAD_REQUEST)

                    .body(new ApiError("INVALID_MODEL", e.getMessage()).asMap());

        }

        String jobId = "genb_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        JobRecord job = new JobRecord(

                jobId,

                projectId,

                ownerId,

                "GENERATE_BATCH",

                storiesPath,

                project.getBaseUrl() == null ? "" : project.getBaseUrl(),

                "",

                "",

                wantReview,

                JobRecord.JobKind.GENERATE_BATCH

        );

        job.setGenerateModel(resolvedModel);

        store.saveJob(job);

        worker.submit(jobId);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(

                "jobId", jobId,

                "status", JobRecord.Status.QUEUED.name(),

                "statusUrl", "/status?jobId=" + jobId

        ));

    }



    private static boolean parseReviewPass(String reviewPass) {

        return "true".equalsIgnoreCase(reviewPass)

                || "1".equals(reviewPass)

                || "on".equalsIgnoreCase(reviewPass);

    }

}


