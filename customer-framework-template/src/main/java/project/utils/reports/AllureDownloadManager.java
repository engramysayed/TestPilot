package project.utils.reports;
import org.jsoup.Jsoup;
import project.utils.Logs.LogsManager;
import project.utils.Terminal;
import project.utils.dataReader.PropertyReader;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class AllureDownloadManager {

    private static String getLatestVersion() {
         try {
            String url = Jsoup.connect("https://github.com/allure-framework/allure2/releases/tag/2.35.1")
                    .followRedirects(true).execute().url().toString();
            if (url.isEmpty()) {
                LogsManager.error("Failed to find the latest version of Allure from Maven Central");
            }else{
                return url.split("/tag/")[1];
            }
        } catch (Exception e) {
            LogsManager.error("Failed to find the latest version of Allure from Maven Central - " + e.getMessage());
            return "";
          }
        return "";
        }


    private static Path downloadZipFile(String version) {

            String downloadUrl = AllurePaths.ALLURE_ZIP_BASE_URL
                    + version + "/allure-commandline-" + version + ".zip";

                try {
                    Path zipFile = Paths.get(AllurePaths.EXTRACTION_DIR.toString(),
                            "allure-" + version + ".zip");

                    if (!Files.exists(zipFile)) {
                        Files.createDirectories(AllurePaths.EXTRACTION_DIR);

                        try (BufferedInputStream in =
                                     new BufferedInputStream(new URI(downloadUrl).toURL().openStream());
                             OutputStream out = Files.newOutputStream(zipFile)) {

                            in.transferTo(out);

                        } catch (Exception e) {
                            LogsManager.error("Invalid URL for Allure download: " + e.getMessage());
                         }
                    }
                    return zipFile;

                } catch (Exception e) {
                    LogsManager.error("Error downloading Allure zip file: " + e.getMessage());
                     return Paths.get("");
                }
            }


    private static void extractZip(Path zipPath) {
        try (ZipInputStream zipInputStream =
                     new ZipInputStream(Files.newInputStream(zipPath))) {

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {

                Path filePath = Paths.get(
                        AllurePaths.EXTRACTION_DIR.toString(),
                        File.separator,
                        entry.getName()
                );

                if (entry.isDirectory()) {
                    Files.createDirectories(filePath);
                } else {
                    Files.createDirectories(filePath.getParent());
                    Files.copy(zipInputStream, filePath);
                }
            }

        } catch (Exception e) {
            LogsManager.error("Error extracting Allure zip file"+ e.getMessage());
         }
    }

    public static Path getExecutable() {

        String version = getLatestVersion();

        Path binaryPath = Paths.get(
                AllurePaths.EXTRACTION_DIR.toString(),
                "allure-" + version,
                "bin",
                "allure"
        );

        return  (PropertyReader.getProperty("OS_NAME").equalsIgnoreCase("Windows"))
                ? binaryPath.resolveSibling(binaryPath.getFileName() + ".bat")
                : binaryPath;
    }

    public static void installAllure() {
         if (!Files.exists(AllurePaths.EXTRACTION_DIR)) {
            //TODO if not windows os need to give permission read and write to install allure before extracting it and after downloading it

            extractZip(downloadZipFile(getLatestVersion()));
            if (!PropertyReader.getProperty("OS_NAME").equals("Windows")) {
                Terminal.executeCommand(
                        "chmod"+
                        "u+x"+
                        getExecutable().toString()
                );
            }
            LogsManager.info("Allure installed successfully");
            try {
                Files.deleteIfExists(Files.list(AllurePaths.EXTRACTION_DIR).filter(path -> path.toString().endsWith(".zip")).findFirst().orElse(null));
                LogsManager.info("Allure zip file deleted successfully after extraction");
            }
            catch (Exception e) {
                LogsManager.error("Error deleting Allure zip file after extraction - " + e.getMessage());
             }
        }else {
            LogsManager.info("Allure already installed");

        }
      }


}