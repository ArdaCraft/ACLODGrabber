package mc.ardacraft.aclodgrabber.screen;

import mc.ardacraft.aclodgrabber.ACLODGrabber;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;

public class DownloadProgressScreen extends Screen {
    private final MinecraftClient client;
    public int progress = 0;
    private String status = "";
    private long startTime;
    private long lastUpdateTime;
    private boolean downloadFailed = false;
    private long totalBytes = 0;
    private long downloadedBytes = 0;
    private String downloadSpeed = "";
    private String timeLeft = "";
    private boolean isHoveringWebsite = false;
    private boolean isHoveringDiscord = false;
    private long serverLastModified = 0;

    public DownloadProgressScreen() {
        super(Text.translatable("screen.aclodgrabber.download.title"));
        this.client = MinecraftClient.getInstance();
        this.startTime = System.currentTimeMillis();
        this.lastUpdateTime = startTime;
    }

    @Override
    protected void init() {
        this.clearChildren();
        
        int buttonWidth = 100;
        int buttonHeight = 20;
        int spacing = 10;
        int buttonY = this.height / 2 + 80;

        if (downloadFailed) {
            int startX = (this.width - (buttonWidth * 2 + spacing)) / 2;

            this.addDrawableChild(ButtonWidget.builder(
                            Text.translatable("screen.aclodgrabber.download.cancel"),
                            button -> {
                                cancelDownload();
                                client.setScreen(new net.minecraft.client.gui.screen.TitleScreen());
                            })
                    .dimensions(startX, buttonY, buttonWidth, buttonHeight)
                    .build());

            this.addDrawableChild(ButtonWidget.builder(
                            Text.translatable("screen.aclodgrabber.download.retry"),
                            button -> {
                                downloadFailed = false;
                                this.status = "";
                                this.progress = 0;
                                this.downloadSpeed = "";
                                this.timeLeft = "";
                                this.startTime = System.currentTimeMillis();
                                this.lastUpdateTime = startTime;
                                this.clearChildren();
                                retryDownload();
                            })
                    .dimensions(startX + buttonWidth + spacing, buttonY, buttonWidth, buttonHeight)
                    .build());
        } else {
            this.addDrawableChild(ButtonWidget.builder(
                            Text.translatable("screen.aclodgrabber.download.cancel"),
                            button -> {
                                cancelDownload();
                                client.setScreen(new net.minecraft.client.gui.screen.TitleScreen());
                            })
                    .dimensions(this.width / 2 - buttonWidth / 2, buttonY, buttonWidth, buttonHeight)
                    .build());
        }
    }

    private void updateHoverState(double mouseX, double mouseY) {
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int linkY = centerY + 35;
        
        String learnMorePrefix = Text.translatable("screen.aclodgrabber.download.learn_more").getString();
        String communityPrefix = Text.translatable("screen.aclodgrabber.download.join_community").getString();
        String websiteUrl = "ardacraft.me";
        String discordUrl = "discord.gg/qcYBkCmAKZ";
        
        int learnMorePrefixWidth = this.textRenderer.getWidth(learnMorePrefix);
        int communityPrefixWidth = this.textRenderer.getWidth(communityPrefix);
        int websiteUrlWidth = this.textRenderer.getWidth(websiteUrl);
        int discordUrlWidth = this.textRenderer.getWidth(discordUrl);
        
        int learnMoreStartX = centerX - (learnMorePrefixWidth + websiteUrlWidth) / 2;
        int communityStartX = centerX - (communityPrefixWidth + discordUrlWidth) / 2;
        
        int websiteUrlStartX = learnMoreStartX + learnMorePrefixWidth;
        int discordUrlStartX = communityStartX + communityPrefixWidth;
        
        isHoveringWebsite = mouseY >= linkY && mouseY < linkY + 12 && 
                           mouseX >= websiteUrlStartX && mouseX < websiteUrlStartX + websiteUrlWidth;
        
        isHoveringDiscord = mouseY >= linkY + 12 && mouseY < linkY + 24 && 
                           mouseX >= discordUrlStartX && mouseX < discordUrlStartX + discordUrlWidth;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw simple dark background instead of renderBackground to avoid blur conflicts
        context.fill(0, 0, this.width, this.height, 0xC0101010);
        updateHoverState(mouseX, mouseY);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        String titleText;
        if (downloadFailed) {
            titleText = this.status;
        } else if (!this.status.isEmpty()) {
            titleText = this.status;
        } else {
            titleText = Text.translatable("screen.aclodgrabber.download.title", progress).getString();
        }

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                titleText,
                centerX,
                centerY - 60,
                downloadFailed || status.contains("failed") || status.contains("required") ? 0xFF5555 : 0xFFFFFF
        );

        if (!downloadSpeed.isEmpty()) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.translatable("screen.aclodgrabber.download.speed", downloadSpeed + " (avg)").getString(),
                    centerX,
                    centerY - 30,
                    0xFFFFFF
            );
        }

        if (!timeLeft.isEmpty()) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.translatable("screen.aclodgrabber.download.time_left", timeLeft + " (est)").getString(),
                    centerX,
                    centerY - 15,
                    0xFFFFFF
            );
        }

        int barWidth = 200;
        int barHeight = 20;
        int barX = centerX - barWidth / 2;
        int barY = centerY + 5;

        context.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0xFF000000);
        context.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);
        
        if (progress > 0) {
            int fillWidth = (int) (barWidth * (progress / 100.0));
            context.fill(barX, barY, barX + fillWidth, barY + barHeight, 0xFF00AA00);
        }

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                progress + "%",
                centerX,
                barY + (barHeight - 8) / 2,
                0xFFFFFF
        );

        int linkY = centerY + 35;
        
        String learnMorePrefix = Text.translatable("screen.aclodgrabber.download.learn_more").getString();
        String communityPrefix = Text.translatable("screen.aclodgrabber.download.join_community").getString();
        String websiteUrl = "ardacraft.me";
        String discordUrl = "discord.gg/qcYBkCmAKZ";
        
        int learnMorePrefixWidth = this.textRenderer.getWidth(learnMorePrefix);
        int communityPrefixWidth = this.textRenderer.getWidth(communityPrefix);
        int websiteUrlWidth = this.textRenderer.getWidth(websiteUrl);
        int discordUrlWidth = this.textRenderer.getWidth(discordUrl);
        
        int learnMoreStartX = centerX - (learnMorePrefixWidth + websiteUrlWidth) / 2;
        int communityStartX = centerX - (communityPrefixWidth + discordUrlWidth) / 2;
        
        context.drawTextWithShadow(
                this.textRenderer,
                learnMorePrefix,
                learnMoreStartX,
                linkY,
                0xAAAAAA
        );
        
        context.drawTextWithShadow(
                this.textRenderer,
                websiteUrl,
                learnMoreStartX + learnMorePrefixWidth,
                linkY,
                isHoveringWebsite ? 0x00DDFF : 0x00AAFF
        );
        
        context.drawTextWithShadow(
                this.textRenderer,
                communityPrefix,
                communityStartX,
                linkY + 12,
                0xAAAAAA
        );
        
        context.drawTextWithShadow(
                this.textRenderer,
                discordUrl,
                communityStartX + communityPrefixWidth,
                linkY + 12,
                isHoveringDiscord ? 0x00DDFF : 0x00AAFF
        );

        super.render(context, mouseX, mouseY, delta);
    }

    // Mouse click handling removed - will use default behavior from parent class
    // The mouse click API changed in 1.21.11 and the link functionality
    // has been temporarily disabled pending yarn mapping updates
    
    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(mouseX, mouseY);
        updateHoverState(mouseX, mouseY);
    }

    public void setProgress(int progress, String status) {
        this.progress = progress;
        this.status = status;
        this.lastUpdateTime = System.currentTimeMillis();
    }

    public void updateDownloadProgress(long bytesDownloaded, long totalBytes) {
        this.downloadedBytes = bytesDownloaded;
        this.totalBytes = totalBytes;
        
        if (totalBytes > 0) {
            this.progress = (int) ((bytesDownloaded * 100) / totalBytes);
        }
        
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
        
        if (elapsedTime > 0 && bytesDownloaded > 0) {
            double bytesPerSecond = (bytesDownloaded * 1000.0) / elapsedTime;
            this.downloadSpeed = formatBytes(bytesPerSecond) + "/s";
            
            if (bytesPerSecond > 0 && totalBytes > bytesDownloaded) {
                long remainingBytes = totalBytes - bytesDownloaded;
                long remainingTime = (long) (remainingBytes / bytesPerSecond);
                this.timeLeft = formatTime(remainingTime);
            }
        }
        
        this.lastUpdateTime = currentTime;
    }

    private String formatBytes(double bytes) {
        if (bytes < 1024) return String.format("%.1f B", bytes);
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024 * 1024));
        return String.format("%.1f GB", bytes / (1024 * 1024 * 1024));
    }

    private String formatTime(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }

    public void markDownloadFailed() {
        this.downloadFailed = true;
        this.status = Text.translatable("screen.aclodgrabber.download.failed").getString();
        this.progress = 0;
        this.init();
    }

    public void setServerLastModified(long serverLastModified) {
        this.serverLastModified = serverLastModified;
    }

    public void onDownloadComplete() {
        this.progress = 100;
        this.downloadSpeed = "";
        this.timeLeft = "";
        this.status = Text.translatable("screen.aclodgrabber.download.complete").getString();
        ACLODGrabber.getInstance().onDownloadComplete();
        client.setScreen(new net.minecraft.client.gui.screen.TitleScreen());
    }

    private void retryDownload() {
        ACLODGrabber.getInstance().retryDownload();
    }

    private void cancelDownload() {
        ACLODGrabber.getInstance().cancelDownload();
    }
}
