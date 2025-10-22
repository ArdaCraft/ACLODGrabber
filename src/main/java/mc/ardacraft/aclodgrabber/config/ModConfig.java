package mc.ardacraft.aclodgrabber.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ModConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("aclodgrabber");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "ACLODGrabber/config.json");
    
    public long lastDownloadTime = 0;
    
    public static ModConfig load() {
        if (!CONFIG_FILE.exists()) {
            return new ModConfig();
        }
        
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            return GSON.fromJson(reader, ModConfig.class);
        } catch (IOException e) {
            LOGGER.error("Failed to load config", e);
            return new ModConfig();
        }
    }
    
    public void save() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save config", e);
        }
    }
    
    public void reset() {
        lastDownloadTime = 0;
        save();
    }
}
