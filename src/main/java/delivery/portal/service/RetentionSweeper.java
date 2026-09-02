package delivery.portal.service;



import delivery.portal.DeliveryPortalProperties;

import delivery.store.ProjectStore;

import org.slf4j.Logger;

import org.slf4j.LoggerFactory;

import org.springframework.scheduling.annotation.Scheduled;

import org.springframework.stereotype.Component;



import java.io.IOException;

import java.nio.file.FileVisitResult;

import java.nio.file.Files;

import java.nio.file.Path;

import java.nio.file.SimpleFileVisitor;

import java.nio.file.attribute.BasicFileAttributes;

import java.time.Instant;

import java.time.temporal.ChronoUnit;

import java.util.ArrayList;

import java.util.List;



@Component

public class RetentionSweeper {



    private static final Logger log = LoggerFactory.getLogger(RetentionSweeper.class);



    private final DeliveryPortalProperties props;



    public RetentionSweeper(DeliveryPortalProperties props) {

        this.props = props;

    }



    @Scheduled(cron = "0 0 3 * * *")

    public void scheduledSweep() {

        sweep();

    }



    public void sweep() {

        int days = props.getRetention().getDays();

        if (days <= 0) {

            return;

        }

        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);

        sweepExecuteRuns(Path.of(props.getStoreRoot()), cutoff);

        sweepWorkDirChildren(Path.of(props.getWorkDir()), cutoff);

    }



    private void sweepExecuteRuns(Path storeRoot, Instant cutoff) {

        if (!Files.isDirectory(storeRoot)) {

            return;

        }

        try {

            for (Path executeRuns : findExecuteRunsDirs(storeRoot)) {

                sweepAgedChildren(executeRuns, cutoff);

            }

        } catch (IOException e) {

            log.warn("Retention sweep skipped execute-runs under {}: {}", storeRoot, e.toString());

        }

    }



    private void sweepWorkDirChildren(Path workDir, Instant cutoff) {

        if (!Files.isDirectory(workDir)) {

            return;

        }

        try {

            sweepAgedChildren(workDir, cutoff);

        } catch (IOException e) {

            log.warn("Retention sweep skipped work dir {}: {}", workDir, e.toString());

        }

    }



    private List<Path> findExecuteRunsDirs(Path storeRoot) throws IOException {

        List<Path> found = new ArrayList<>();

        Files.walkFileTree(storeRoot, new SimpleFileVisitor<>() {

            @Override

            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {

                String name = dir.getFileName().toString();

                if ("generated".equals(name)) {

                    return FileVisitResult.SKIP_SUBTREE;

                }

                if ("execute-runs".equals(name)) {

                    found.add(dir);

                    return FileVisitResult.SKIP_SUBTREE;

                }

                return FileVisitResult.CONTINUE;

            }

        });

        return found;

    }



    private void sweepAgedChildren(Path parent, Instant cutoff) throws IOException {

        try (var children = Files.list(parent)) {

            children.filter(Files::isDirectory)

                    .filter(child -> isOlderThan(child, cutoff))

                    .forEach(this::deleteQuietly);

        }

    }



    private boolean isOlderThan(Path dir, Instant cutoff) {

        try {

            return Files.getLastModifiedTime(dir).toInstant().isBefore(cutoff);

        } catch (IOException e) {

            return false;

        }

    }



    private void deleteQuietly(Path dir) {

        try {

            ProjectStore.deleteRecursive(dir);

            log.info("Retention deleted aged dir {}", dir);

        } catch (Exception e) {

            log.warn("Retention failed to delete {}: {}", dir, e.toString());

        }

    }

}

