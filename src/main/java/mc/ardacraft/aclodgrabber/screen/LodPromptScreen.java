package mc.ardacraft.aclodgrabber.screen;

import mc.ardacraft.aclodgrabber.ACLODGrabber;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class LodPromptScreen extends Screen {
    private final MinecraftClient client;

    public LodPromptScreen() {
        super(Text.translatable("screen.aclodgrabber.title"));
        this.client = MinecraftClient.getInstance();
    }

    @Override
    protected void init() {
        int buttonWidth = 120;
        int buttonHeight = 20;
        int spacing = 10;
        int startX = (this.width - (buttonWidth * 2 + spacing)) / 2;
        int y = this.height / 2 + 20;

        this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.aclodgrabber.accept"),
                        button -> onAcceptClicked())
                .dimensions(startX, y, buttonWidth, buttonHeight)
                .build());

        this.addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.aclodgrabber.notnow"),
                        button -> onNotNowClicked())
                .dimensions(startX + buttonWidth + spacing, y, buttonWidth, buttonHeight)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Draw simple dark background instead of renderBackground to avoid blur conflicts
        context.fill(0, 0, this.width, this.height, 0xC0101010);

        String[] descriptionLines = getDescriptionLines();
        int lineHeight = 12;
        int startY = this.height / 2 - (descriptionLines.length * lineHeight) / 2 - 20;

        for (int i = 0; i < descriptionLines.length; i++) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.translatable(descriptionLines[i]),
                    this.width / 2,
                    startY + (i * lineHeight),
                    0xFFFFFF
            );
        }

        super.render(context, mouseX, mouseY, delta);
        
        int buttonY = height / 2 + 20;
        int messageY = buttonY + 30;
        
        context.drawCenteredTextWithShadow(
                textRenderer,
                Text.translatable("screen.aclodgrabber.space_warning").formatted(Formatting.YELLOW),
                width / 2,
                messageY,
                0xFFFFFF
        );
    }

    @Override
    public void close() {
        onNotNowClicked();
        client.setScreen(new net.minecraft.client.gui.screen.TitleScreen());
    }

    private void onAcceptClicked() {
        ACLODGrabber.getInstance().acceptUpdate();
    }

    private void onNotNowClicked() {
        ACLODGrabber.getInstance().notNowUpdate();
    }

    private String[] getDescriptionLines() {
        return new String[]{
                "screen.aclodgrabber.update.line1",
                "screen.aclodgrabber.update.line2",
                "screen.aclodgrabber.update.line3"
        };
    }
}
