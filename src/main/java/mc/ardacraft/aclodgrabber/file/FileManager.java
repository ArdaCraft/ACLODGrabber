package mc.ardacraft.aclodgrabber.file;

import net.fabricmc.loader.api.FabricLoader;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;

public class FileManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("aclodgrabber.file");
    private static final String DH_FOLDER = "Distant_Horizons_server_data";
    private static final long MIN_FREE_SPACE_BYTES = 10L * 1024 * 1024 * 1024;
    
    public static Path getMinecraftDirectory() {
        return FabricLoader.getInstance().getGameDir();
    }
    
    public static boolean hasEnoughSpace() {
        try {
            Path gameDir = getMinecraftDirectory();
            long freeSpace = Files.getFileStore(gameDir).getUsableSpace();
            return freeSpace >= MIN_FREE_SPACE_BYTES;
        } catch (IOException e) {
            LOGGER.warn("Could not check available disk space", e);
            return true;
        }
    }
    
    public static Path getLODsDirectory() {
        var dir = getMinecraftDirectory().resolve(DH_FOLDER);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            LOGGER.error("Failed to create LODs directory: " + dir, e);
        }
        return dir;
    }
    
    public static boolean hasExistingLODs() {
        return Files.exists(getLODsDirectory());
    }
    
    public static void deleteExistingLODs() throws IOException {
        Path lodDir = getLODsDirectory();
        if (Files.exists(lodDir)) {
            Files.walk(lodDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            LOGGER.warn("Failed to delete file: " + path, e);
                        }
                    });
        }
    }
    
    public static void extractZipFile(InputStream zipStream, DownloadProgressCallback callback) throws IOException {
        if (!hasEnoughSpace()) {
            throw new IOException("Not enough disk space. At least 10GB required.");
        }
        
        Path tempFile = null;
        try {
            Path temFilePath = getLODsDirectory().resolve("aclodgrabber.zip");
            if(Files.exists(temFilePath)) {
                Files.delete(temFilePath);
            }
            tempFile = Files.createFile(getLODsDirectory().resolve("aclodgrabber.zip"));
            //tempFile = Files.createTempFile("aclodgrabber", ".zip");
            LOGGER.debug("Created temporary file: {}", tempFile);
            
            try (FileOutputStream fos = new FileOutputStream(tempFile.toFile())) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytes = 0;
                
                while ((bytesRead = zipStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                    
                    if (callback != null) {
                        callback.onProgress(totalBytes, -1);
                    }
                }
                
                LOGGER.debug("Downloaded {} bytes to temp file", totalBytes);
            }
            
            try (ZipFile zipFile = new ZipFile(tempFile.toFile())) {
                var entries = zipFile.getEntries();
                int processedEntries = 0;
                int totalEntries = 0;
                
                while (entries.hasMoreElements()) {
                    entries.nextElement();
                    totalEntries++;
                }
                
                LOGGER.debug("Found {} entries in ZIP file", totalEntries);
                
                entries = zipFile.getEntries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    if (!entry.isDirectory()) {
                        Path targetPath = getMinecraftDirectory().resolve(entry.getName());
                        Files.createDirectories(targetPath.getParent());
                        
                        try (InputStream entryStream = zipFile.getInputStream(entry)) {
                            Files.copy(entryStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    
                    processedEntries++;
                    if (callback != null) {
                        callback.onProgress(-1, (int) ((processedEntries * 100.0) / totalEntries));
                    }
                }
                
                LOGGER.info("Successfully extracted {} entries", processedEntries);
            }
            
        } catch (IOException e) {
            LOGGER.error("Failed to extract ZIP file", e);
            throw e;
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                    LOGGER.debug("Cleaned up temporary file: {}", tempFile);
                } catch (IOException e) {
                    LOGGER.warn("Failed to delete temporary file: {}", tempFile, e);
                }
            }
        }
    }
    
    public interface DownloadProgressCallback {
        void onProgress(long bytesDownloaded, int extractionProgress);
    }
}
