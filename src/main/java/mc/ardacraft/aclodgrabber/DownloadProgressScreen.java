package mc.ardacraft.aclodgrabber;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class DownloadProgressScreen extends Screen {
    private final MinecraftClient client;
    final Runnable onComplete;
    private String status = "Initializing download...";
    private int progress = 0;

    public DownloadProgressScreen(MinecraftClient client, Runnable onComplete) {
        super(Text.literal("Downloading LODs"));
        this.client = client;
        this.onComplete = onComplete;
    }

    // Use this method to update the progress dynamically
    public void setProgress(int progress, String status) {
        this.progress = progress;
        this.status = status;
    }

    @Override
    protected void init() {
        // Add a cancel button
        this.addDrawableChild(ButtonWidget.builder(
                        Text.literal("Cancel"),
                        button -> {
                            cancelDownload();
                            client.setScreen(new TitleScreen());
                        })
                .dimensions(this.width / 2 - 50, this.height / 2 + 50, 100, 20)
                .build());
    }

    private void cancelDownload() {
        // Cancel the download logic here if applicable
        ACLODGrabber.isCurrentlyDownloading = false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        // Draw the progress text info
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal(this.status),
                this.width / 2,
                this.height / 2 - 30,
                0xFFFFFF
        );

        // Draw the progress bar
        int barWidth = 200;
        int barHeight = 10;
        int startX = (width - barWidth) / 2;
        int startY = height / 2;

        context.fill(startX, startY, startX + barWidth, startY + barHeight, 0xFF000000); // Background
        context.fill(startX, startY, startX + (int) (barWidth * (progress / 100.0)), startY + barHeight, 0xFF00FF00); // Progress

        // Render any buttons
        super.render(context, mouseX, mouseY, delta);
    }
}