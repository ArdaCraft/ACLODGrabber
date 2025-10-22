package mc.ardacraft.aclodgrabber;

import mc.ardacraft.aclodgrabber.config.ModConfig;
import mc.ardacraft.aclodgrabber.file.FileManager;
import mc.ardacraft.aclodgrabber.network.NetworkManager;
import mc.ardacraft.aclodgrabber.screen.DownloadProgressScreen;
import mc.ardacraft.aclodgrabber.screen.LodPromptScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.HttpURLConnection;

public class ACLODGrabber implements ModInitializer, ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("aclodgrabber");
    
    private static ACLODGrabber instance;
    private static ModConfig config;
    private static boolean isCurrentlyDownloading = false;
    private boolean promptSuppressedUntilRestart = false;

    public static ACLODGrabber getInstance() {
        return instance;
    }

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing ACLODGrabber...");
        instance = this;
        config = ModConfig.load();
        registerCommands();
    }

    @Override
    public void onInitializeClient() {
        registerCommands();
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("resetLODs")
                    .executes(context -> {
                        try {
                            config.reset();
                            context.getSource().sendFeedback(Text.translatable("command.resetLODs.success"));
                            return 1;
                        } catch (Exception e) {
                            context.getSource().sendError(Text.translatable("command.resetLODs.error"));
                            return 0;
                        }
                    }));
        });
    }

    public void checkForUpdates(MinecraftClient client) {
        if (promptSuppressedUntilRestart) {
            return;
        }

        new Thread(() -> {
            try {
                long serverLastModified = NetworkManager.getServerLastModified();
                long lastDownloadTime = config.lastDownloadTime;

                LOGGER.info("Update check - Server: {}, LastDownload: {}", 
                           serverLastModified, lastDownloadTime);

                boolean shouldPrompt = false;
                
                if (serverLastModified > lastDownloadTime || lastDownloadTime == 0) {
                    shouldPrompt = true;
                    if (lastDownloadTime == 0) {
                        LOGGER.info("Should prompt: First time download (lastDownloadTime = 0)");
                    } else {
                        LOGGER.info("Should prompt: Server version {} > last download time {}", 
                                   serverLastModified, lastDownloadTime);
                    }
                } else {
                    LOGGER.info("No update needed: Server version {} <= last download time {}", 
                               serverLastModified, lastDownloadTime);
                }

                if (shouldPrompt) {
                    promptForUpdate(client, (int) (serverLastModified / 1000));
                }
            } catch (Exception e) {
                LOGGER.error("Failed to check for updates", e);
            }
        }).start();
    }

    private void promptForUpdate(MinecraftClient client, int latestVersion) {
        LOGGER.info("promptForUpdate called with version: {}", latestVersion);
        if (promptSuppressedUntilRestart) {
            LOGGER.info("Prompt suppressed - not showing screen");
            return;
        }
        LOGGER.info("Setting screen to LodPromptScreen");
        client.execute(() -> {
            client.setScreen(new LodPromptScreen());
        });
    }

    public void acceptUpdate() {
        onAcceptUpdate(MinecraftClient.getInstance(), (int) (config.lastDownloadTime / 1000));
    }

    public void notNowUpdate() {
        onNotNowUpdate(MinecraftClient.getInstance());
    }

    public void initiateDownload() {
        initiateDownloadInternal(MinecraftClient.getInstance());
    }

    public void onDownloadComplete() {
        LOGGER.info("Download and installation complete!");
    }


    public void retryDownload() {
        LOGGER.info("Retrying download...");
        initiateDownloadInternal(MinecraftClient.getInstance());
    }

    public void cancelDownload() {
        isCurrentlyDownloading = false;
        LOGGER.info("Download cancelled by user");
    }

    private void onAcceptUpdate(MinecraftClient client, int latestVersion) {
        initiateDownloadInternal(client);
    }

    private void onNotNowUpdate(MinecraftClient client) {
        promptSuppressedUntilRestart = true;
        client.setScreen(new net.minecraft.client.gui.screen.TitleScreen());
    }

    private void initiateDownloadInternal(MinecraftClient client) {
        if (isCurrentlyDownloading) {
            LOGGER.warn("A download is already in progress. Ignored new request.");
            return;
        }
        client.execute(() -> {
            DownloadProgressScreen downloadScreen = new DownloadProgressScreen();
            client.setScreen(downloadScreen);
            downloadAndInstallLODs(client, downloadScreen);
        });
    }

    private void downloadAndInstallLODs(MinecraftClient client, DownloadProgressScreen downloadScreen) {
        isCurrentlyDownloading = true;
        
        new Thread(() -> {
            try {
                downloadScreen.setProgress(0, "");
                
                HttpURLConnection connection = NetworkManager.createDownloadConnection();
                long contentLength = connection.getContentLengthLong();
                long serverLastModified = connection.getLastModified();
                
                downloadScreen.setServerLastModified(serverLastModified);
                
                try (InputStream inputStream = connection.getInputStream()) {
                    FileManager.extractZipFile(inputStream, (bytesDownloaded, extractionProgress) -> {
                        client.execute(() -> {
                            if (client.currentScreen instanceof DownloadProgressScreen dps) {
                                if (bytesDownloaded >= 0) {
                                    dps.updateDownloadProgress(bytesDownloaded, contentLength);
                                } else if (extractionProgress >= 0) {
                                    dps.setProgress(100, Text.translatable("screen.aclodgrabber.download.extracting").getString());
                                }
                            }
                        });
                    });
                }
                
                config.lastDownloadTime = System.currentTimeMillis();
                config.save();
                
                client.execute(() -> {
                    if (client.currentScreen instanceof DownloadProgressScreen dps) {
                        dps.setProgress(100, Text.translatable("screen.aclodgrabber.download.complete").getString());
                        dps.onDownloadComplete();
                    }
                });
                
            } catch (Exception e) {
                LOGGER.error("Download failed", e);
                client.execute(() -> {
                    if (client.currentScreen instanceof DownloadProgressScreen dps) {
                        dps.markDownloadFailed();
                    }
                });
            } finally {
                isCurrentlyDownloading = false;
            }
        }).start();
    }

    static {
        LOGGER.info("Registering CLIENT_STARTED event");
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            LOGGER.info("CLIENT_STARTED event triggered");
            ACLODGrabber grabber = ACLODGrabber.getInstance();
            if (grabber != null) {
                LOGGER.info("ACLODGrabber instance found, calling checkForUpdates");
                grabber.checkForUpdates(client);
            } else {
                LOGGER.warn("ACLODGrabber instance is null!");
            }
        });
    }
}