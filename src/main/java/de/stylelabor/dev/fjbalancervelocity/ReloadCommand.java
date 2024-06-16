package de.stylelabor.dev.fjbalancervelocity;

import com.velocitypowered.api.command.SimpleCommand;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ReloadCommand implements SimpleCommand {

    private final FJ_Balancer_VELOCITY plugin;
    private final Logger logger;

    public ReloadCommand(FJ_Balancer_VELOCITY plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    @Override
    public void execute(Invocation invocation) {
        File joinedPlayersFile = plugin.getJoinedPlayersFile();
        File lastServerFile = plugin.getLastServerFile();

        Set<UUID> joinedPlayers = new HashSet<>();
        Map<UUID, String> lastServerData = new HashMap<>();

        try (FileReader reader = new FileReader(joinedPlayersFile)) {
            Yaml yaml = new Yaml();
            Set<UUID> loadedPlayers = yaml.load(reader);
            if (loadedPlayers != null) {
                joinedPlayers.addAll(loadedPlayers);
            }
            logger.info("Reloaded joined players from file");
        } catch (IOException e) {
            logger.error("Failed to reload joined players", e);
        }

        try (FileReader reader = new FileReader(lastServerFile)) {
            Yaml yaml = new Yaml();
            Map<UUID, String> loadedLastServerData = yaml.load(reader);
            if (loadedLastServerData != null) {
                lastServerData.putAll(loadedLastServerData);
            }
            logger.info("Reloaded last server data from file");
        } catch (IOException e) {
            logger.error("Failed to reload last server data", e);
        }

        plugin.setJoinedPlayers(joinedPlayers);
        plugin.setLastServerData(lastServerData);
    }
}