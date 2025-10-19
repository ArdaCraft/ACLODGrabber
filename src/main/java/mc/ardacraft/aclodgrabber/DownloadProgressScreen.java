package mc.ardacraft.aclodgrabber;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

public class DownloadProgressScreen extends Screen {
    private final MinecraftClient client;
    final Runnable onComplete;
    private String status = "Initializing download...";
    private int progress = 0;
    private String downloadSpeed = "";
    private String timeLeft = "";
    private long startTime = 0;
    private long lastUpdateTime = 0;
    private long lastDownloadedBytes = 0;
    
    private long totalDownloadedBytes = 0;
    private long totalElapsedTime = 0;
    private double averageSpeedBps = 0;

    public DownloadProgressScreen(MinecraftClient client, Runnable onComplete) {
        super(Text.literal("Downloading LODs"));
        this.client = client;
        this.onComplete = onComplete;
        this.startTime = System.currentTimeMillis();
        this.lastUpdateTime = startTime;
    }

    // Use this method to update the progress dynamically
    public void setProgress(int progress, String status) {
        this.progress = progress;
        this.status = status;
    }

    public void setProgress(int progress, String status, long downloadedBytes, long totalBytes) {
        this.progress = progress;
        this.status = status;
        
        long currentTime = System.currentTimeMillis();
        
        totalDownloadedBytes = downloadedBytes;
        totalElapsedTime = currentTime - startTime;
        
        if (totalElapsedTime > 2000) {
            averageSpeedBps = (totalDownloadedBytes * 1000.0) / totalElapsedTime;
            
            if (averageSpeedBps > 1024 * 1024) {
                downloadSpeed = String.format("%.1f MB/s (avg)", averageSpeedBps / (1024 * 1024));
            } else if (averageSpeedBps > 1024) {
                downloadSpeed = String.format("%.1f KB/s (avg)", averageSpeedBps / 1024);
            } else {
                downloadSpeed = String.format("%.0f B/s (avg)", averageSpeedBps);
            }
            
            if (averageSpeedBps > 0 && totalBytes > downloadedBytes) {
                long remainingBytes = totalBytes - downloadedBytes;
                long estimatedTimeMs = (long) (remainingBytes / averageSpeedBps * 1000);
                
                if (estimatedTimeMs > 0) {
                    long seconds = estimatedTimeMs / 1000;
                    long minutes = seconds / 60;
                    long hours = minutes / 60;
                    
                    if (hours > 0) {
                        timeLeft = String.format("%dh %dm (est)", hours, minutes % 60);
                    } else if (minutes > 0) {
                        timeLeft = String.format("%dm %ds (est)", minutes, seconds % 60);
                    } else {
                        timeLeft = String.format("%ds (est)", seconds);
                    }
                } else {
                    timeLeft = "Calculating...";
                }
            } else {
                timeLeft = "Calculating...";
            }
        } else {
            downloadSpeed = "Calculating...";
            timeLeft = "Calculating...";
        }
    }

    @Override
    protected void init() {
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("Cancel"),
                        button -> {
                            cancelDownload();
                            client.setScreen(new TitleScreen());
                        })
                .dimensions(this.width / 2 - 50, this.height / 2 + 80, 100, 20)
                .build());
    }

    private void cancelDownload() {
        // Cancel the download logic here if applicable
        ACLODGrabber.isCurrentlyDownloading = false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int linkY = height / 2 + 50;
        
        if (mouseY >= linkY && mouseY <= linkY + 10) {
            Util.getOperatingSystem().open("https://ardacraft.me");
            return true;
        }
        
        if (mouseY >= linkY + 15 && mouseY <= linkY + 25) {
            Util.getOperatingSystem().open("https://discord.com/invite/qcYBkCmAKZ");
            return true;
        }
        
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal(this.status),
                this.width / 2,
                this.height / 2 - 50,
                0xFFFFFF
        );

        boolean isInstalling = status.toLowerCase().contains("install");
        
        if (!downloadSpeed.isEmpty() && !isInstalling) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("Speed: " + downloadSpeed),
                    this.width / 2,
                    this.height / 2 - 30,
                    0xAAAAAA
            );
        }

        if (!timeLeft.isEmpty() && !isInstalling) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("Time left: " + timeLeft),
                    this.width / 2,
                    this.height / 2 - 10,
                    0xAAAAAA
            );
        }

        // Draw the progress bar
        int barWidth = 200;
        int barHeight = 10;
        int startX = (width - barWidth) / 2;
        int startY = height / 2 + 10;

        context.fill(startX, startY, startX + barWidth, startY + barHeight, 0xFF000000); // Background
        context.fill(startX, startY, startX + (int) (barWidth * (progress / 100.0)), startY + barHeight, 0xFF00FF00); // Progress

        int linkY = startY + barHeight + 30;
        
        boolean websiteHovered = mouseY >= linkY && mouseY <= linkY + 10;
        boolean discordHovered = mouseY >= linkY + 15 && mouseY <= linkY + 25;
        
        Text websiteText = Text.literal("Learn more about us: ").formatted(Formatting.GRAY)
                .append(Text.literal("ardacraft.me").formatted(
                        websiteHovered ? Formatting.AQUA : Formatting.BLUE, 
                        Formatting.UNDERLINE));
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                websiteText,
                this.width / 2,
                linkY,
                0xFFFFFF
        );

        Text discordText = Text.literal("Join our community: ").formatted(Formatting.GRAY)
                .append(Text.literal("discord.gg/qcYBkCmAKZ").formatted(
                        discordHovered ? Formatting.AQUA : Formatting.BLUE, 
                        Formatting.UNDERLINE));
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                discordText,
                this.width / 2,
                linkY + 15,
                0xFFFFFF
        );

        // Render any buttons
        super.render(context, mouseX, mouseY, delta);
    }
}