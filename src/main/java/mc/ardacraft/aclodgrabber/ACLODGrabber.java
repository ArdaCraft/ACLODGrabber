package mc.ardacraft.aclodgrabber;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ACLODGrabber implements ModInitializer, ClientModInitializer {
    private static final String LODS_DOWNLOAD_URL = "http://mc.ardacraft.me:25564/AC_LODS_#.zip";
    private static final String DH_FOLDER = "Distant_Horizons_server_data";
    public static final Logger LOGGER = LoggerFactory.getLogger("aclodgrabber");

    private static Config config;
    protected static boolean isCurrentlyDownloading = false;
    private boolean promptSuppressedUntilRestart = false;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing ACLODGrabber...");
        //registerCommands();
        try {
            // Create base config directory path
            Path configDir = FabricLoader.getInstance().getConfigDir().resolve("ACLODGrabber");
            Path configPath = configDir.resolve("config.json");

            LOGGER.info("Loading config from: " + configPath);
            config = Config.load(configPath);
            LOGGER.info("Config loaded successfully. Download status: " + config.hasDownloadedLODs);

            // Create necessary subdirectories
            Files.createDirectories(configDir.resolve("temp_extract"));

            LOGGER.info("Created necessary directories in: " + configDir);
        } catch (IOException e) {
            LOGGER.error("Failed to initialize mod directories:", e);
            return;
        }

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            // Check current config state
            if (!config.hasDeclined) {
                // Show LodPromptScreen before proceeding to TitleScreen
                client.execute(() -> checkForNewerVersion(client));
            }

        });
    }

    private void promptForUpdate(MinecraftClient client, int latestVersion) {
        // Only show the prompt if not already declined
        if (config.hasDeclined || promptSuppressedUntilRestart) {
            return; // Skip if the user declined or suppressed until restart
        }

        client.execute(() -> {
            client.setScreen(new LodPromptScreen(
                    null, // Return to the current screen afterward
                    () -> onAcceptUpdate(client, latestVersion), // Accept
                    () -> onDeclineUpdate(client),               // Decline
                    () -> onNotNowUpdate(client),                 // Not Now
                    new String[]{"There is a new version of the ArdaCraft LODs available.",
                            "This will allow you to see much farther across the map.",
                            "Would you like to download the updated LODs?"}
            ));
        });
    }

    private void onAcceptUpdate(MinecraftClient client, int latestVersion) {
        LOGGER.info("User accepted the update. Initiating download...");
        config.currentLODVersion = String.valueOf(latestVersion);
        initiateDownload(client); // Start the LODs download

        // Update the Config with the new version
        config.hasDeclined = false;
        try {
            config.save();
        } catch (IOException e) {
            LOGGER.error("Failed to save config after accepting update.", e);
        }

        // Close the prompt
        //client.setScreen(new TitleScreen());
    }

    private void onDeclineUpdate(MinecraftClient client) {
        LOGGER.info("User declined the update. Suppressing future prompts...");

        // Update Config to not show the prompt again
        config.hasDeclined = true;
        try {
            config.save();
        } catch (IOException e) {
            LOGGER.error("Failed to save config after declining update.", e);
        }

        // Close the prompt
        client.setScreen(new TitleScreen());
    }

    private void onNotNowUpdate(MinecraftClient client) {
        LOGGER.info("User chose not to update now. Suppressing prompt until restart...");

        promptSuppressedUntilRestart = true; // Temporarily suppress prompts
        client.setScreen(new TitleScreen()); // Close the prompt
    }


    private int extractVersionNumber(String fileName) {
        // Example: AC_LODS_1.zip, AC_LODS_2.zip
        Pattern pattern = Pattern.compile("AC_LODS_(\\d+)\\.zip");
        Matcher matcher = pattern.matcher(fileName);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return -1; // Return -1 for invalid or unrecognized file names
    }

    private int getStoredVersion() {
        return Integer.parseInt(config.currentLODVersion); // Default to 0 if version not found
    }

    private void checkForNewerVersion(MinecraftClient client) {
        int storedVersion = getStoredVersion(); // Version currently saved in the config
        int latestVersion = determineLatestVersionFromServer(); // Fetch the latest version from the server

        if (latestVersion == -1) {
            LOGGER.warn("Could not determine the latest version from the server.");
            return; // Skip if we couldn't fetch the latest version
        }

        if (latestVersion > storedVersion) {
            LOGGER.info("A newer LODs version ({}) is available!", latestVersion);
            promptForUpdate(client, latestVersion); // Show the update prompt if a newer version is available
        } else {
            LOGGER.info("No new LODs version available. Current version is up to date: {}", storedVersion);
        }
    }

    private int determineLatestVersionFromServer() {
        String serverUrl = "http://mc.ardacraft.me:25564/"; // Base URL of the file server
        String latestFileName = fetchLatestFileName(serverUrl);

        if (latestFileName != null) {
            return extractVersionNumber(latestFileName); // Extract the version number from the file name
        }

        LOGGER.warn("Could not determine the latest version. Returning -1.");
        return -1; // If no valid file is found, return -1
    }

    private String fetchLatestFileName(String serverUrl) {
        try {
            // Make an HTTP request to fetch the HTML response
            HttpURLConnection connection = (HttpURLConnection) new URL(serverUrl).openConnection();
            connection.setRequestMethod("GET");

            // Check if the response is valid
            if (connection.getResponseCode() != 200) {
                LOGGER.error("Failed to fetch data from the server. Response code: " + connection.getResponseCode());
                return null;
            }

            // Read the response body
            StringBuilder responseBody = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    responseBody.append(line).append("\n");
                }
            }

            // Parse the response body and extract file names
            List<String> fileNames = new ArrayList<>();
            Document document = Jsoup.parse(responseBody.toString());
            Elements links = document.select("a");

            for (Element link : links) {
                String href = link.attr("href");
                if (isValidFile(href)) {
                    fileNames.add(href);
                }
            }

            // Log the file names for debugging (optional)
            for (String fileName : fileNames) {
                LOGGER.info("Found file: " + fileName);
            }

            // Return the latest file name, assuming files are sorted or newest is last in list
            return fileNames.isEmpty() ? null : fileNames.get(fileNames.size() - 1);

        } catch (Exception e) {
            LOGGER.error("Error occurred while fetching the latest file name: ", e);
            return null;
        }
    }

    // Helper method: Check if the file name corresponds to valid file
    private boolean isValidFile(String fileName) {
        return fileName.endsWith(".zip") || fileName.endsWith(".txt") ||
                fileName.endsWith(".jar") || fileName.endsWith(".json");
    }

    private void initiateDownload(MinecraftClient client) {
        // Ensure only one download process is allowed at a time
        if (isCurrentlyDownloading) {
            LOGGER.warn("A download is already in progress. Ignored new request.");
            return;
        }

        // Create and set the DownloadProgressScreen
        client.execute(() -> {
            DownloadProgressScreen downloadScreen = new DownloadProgressScreen(client, () -> {
                LOGGER.info("Download and installation complete!");
                client.setScreen(new TitleScreen()); // Proceed to TitleScreen after completion
            });

            client.setScreen(downloadScreen);

            // Start the download process
            downloadAndInstallLODs(client); // Pass client instance for the download
        });
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("resetLODs")
                    .executes(context -> {
                        // Ensure this is a client-side command
                        if (MinecraftClient.getInstance().getNetworkHandler().getConnection() != null) {
                            // Perform your reset logic
                            config.hasDownloadedLODs = false;
                            config.hasDeclined = false;
                            config.currentLODVersion = "0";
                            try {
                                config.save();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            context.getSource()
                                    .sendFeedback(Text.literal("ACLODGrabber config reset.").formatted(Formatting.GOLD));
                        } else {
                            context.getSource()
                                    .sendFeedback(Text.literal("This command cannot be run without a server connection.")
                                            .formatted(Formatting.RED));
                        }
                        return 1;
                    }));
        });
    }

    private void unzipFile(Path zipFile, Path targetDir) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            zip.getEntries().asIterator().forEachRemaining(entry -> {
                Path outputPath = targetDir.resolve(entry.getName());

                if (entry.isDirectory()) {
                    try {
                        Files.createDirectories(outputPath);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    try {
                        // Ensure the parent directories are created
                        Files.createDirectories(outputPath.getParent());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    try (InputStream inputStream = zip.getInputStream(entry)) {
                        // Copy file with REPLACE_EXISTING to ensure overwriting
                        Files.copy(inputStream, outputPath, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
    }


    public void onYesCommand(MinecraftClient client) {
        downloadAndInstallLODs(client);
    }

    public void onNoCommand(MinecraftClient client) {
        config.hasDeclined = true;
        try {
            config.save();
        } catch (IOException e) {
            LOGGER.error("Failed to save config:", e);
        }

        client.player.sendMessage(
                Text.literal("You can always download the LODs later by typing /ac_lod_yes")
                        .formatted(Formatting.YELLOW), false
        );
    }


    private void downloadAndInstallLODs(MinecraftClient client) {
        if (isCurrentlyDownloading) {
            LOGGER.warn("Download already in progress, ignoring new request.");
            //sendProgressMessage(client, "Download already in progress!", Formatting.RED);
            return;
        }

        Thread downloadThread = new Thread(() -> {
            isCurrentlyDownloading = true;

            try {
                Path dhFolder = client.runDirectory.toPath().resolve(DH_FOLDER);
                Path tempFile = dhFolder.resolve("AC_LODS.zip");

                // Ensure the target directory exists
                Files.createDirectories(dhFolder);

                LOGGER.info("Starting the LOD download process");
                //sendProgressMessage(client, "Initializing download...", Formatting.YELLOW);

                LOGGER.info("Downloading LODs version: " + String.valueOf(config.currentLODVersion));
                // Setup HTTP connection
                URL url = new URL(LODS_DOWNLOAD_URL.replace("#", String.valueOf(config.currentLODVersion)));
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestProperty("User-Agent", "ACLODGrabber Mod");
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(30000);

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Server returned HTTP code: " + responseCode);
                }

                long fileSize = connection.getContentLengthLong();
                LOGGER.info("Expected file size: " + fileSize + " bytes");

                // Download the file and write to disk
                try (InputStream inputStream = connection.getInputStream();
                     OutputStream outputStream = Files.newOutputStream(tempFile)) {

                    byte[] buffer = new byte[4096];
                    long downloadedBytes = 0;
                    int bytesRead;

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        downloadedBytes += bytesRead;

                        // Calculate progress
                        int progress = (int) ((downloadedBytes * 100) / fileSize);
                        String status;

                        // Check if download is complete
                        if (progress >= 100) {
                            status = "Installing, please wait...this may take a while...";
                        } else {
                            status = "Downloading: " + progress + "%";
                        }


                        // Update progress in DownloadProgressScreen
                        client.execute(() -> {
                            if (client.currentScreen instanceof DownloadProgressScreen dps) {
                                dps.setProgress(progress, status);
                            }
                        });
                    }

                    LOGGER.info("Download completed successfully.");
                    //sendProgressMessage(client, "Download complete!", Formatting.GREEN);
                }

                // Perform installation logic
                //sendProgressMessage(client, "Installing downloaded files...", Formatting.YELLOW);
                installLODFiles(dhFolder, tempFile); // Reuse "install logic" here

                // Once installation is complete, update progress and transition screens
                client.execute(() -> {
                    if (client.currentScreen instanceof DownloadProgressScreen dps) {
                        dps.setProgress(100, "Installation complete!");
                        dps.onComplete.run(); // Proceed to the next screen after installation
                    }
                });

                LOGGER.info("LOD installation completed successfully!");
            } catch (Exception e) {
                LOGGER.error("Failed to download and install LODs", e);
                // sendProgressMessage(client, "Download failed! Check logs for details.", Formatting.RED);

                client.execute(() -> {
                    if (client.currentScreen instanceof DownloadProgressScreen dps) {
                        dps.setProgress(0, "Download failed.");
                    }
                });
            } finally {
                isCurrentlyDownloading = false;
            }
        });

        downloadThread.start();
    }

    private void installLODFiles(Path dhFolder, Path tempFile) throws IOException {
        // Clean up existing files in dhFolder
        //cleanupExistingFiles(dhFolder);

        // Unzip the downloaded zip file into dhFolder
        unzipFile(tempFile, dhFolder);

        // Delete the temporary downloaded file to save space
        Files.deleteIfExists(tempFile);

        // Update the configuration to mark download as complete
        config.hasDownloadedLODs = true;
        config.save();

        LOGGER.info("Installation complete, and configuration updated!");
    }

    @Override
    public void onInitializeClient() {
        registerCommands();
    }
}