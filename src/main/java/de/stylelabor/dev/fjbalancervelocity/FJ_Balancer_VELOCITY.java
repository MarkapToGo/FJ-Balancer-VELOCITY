package de.stylelabor.dev.fjbalancervelocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "fj-balancer-velocity",
        name = "FJ-Balancer-VELOCITY",
        version = "20.07.2026"
)
public class FJ_Balancer_VELOCITY {

    @Inject
    private Logger logger;

    @Inject
    private ProxyServer server;

    private volatile Set<UUID> joinedPlayers = ConcurrentHashMap.newKeySet();
    private volatile Map<UUID, String> lastServerData = new ConcurrentHashMap<>();
    private File joinedPlayersFile = new File("plugins/Markap-FJ-BALANCER/joinedPlayers.yml");
    private File lastServerFile = new File("plugins/Markap-FJ-BALANCER/last-server-data.yml");

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {

        CommandManager commandManager = server.getCommandManager();
        CommandMeta reloadMeta = commandManager.metaBuilder("fjv-reload").build();
        CommandMeta switchMeta = commandManager.metaBuilder("stylelabor-server").aliases("switch-server").build();

        commandManager.register(reloadMeta, new ReloadCommand(this, logger));
        commandManager.register(switchMeta, new StylelaborServerCommand(this, logger));

        // Schedule the task to reload data every minute using Velocity scheduler
        server.getScheduler().buildTask(this, this::reloadDataFromFile)
                .repeat(60, TimeUnit.SECONDS)
                .schedule();

        logger.info("\n################################\n##                            ##\n##   FJ Balancer [Velocity]   ##\n##      coded by Markap       ##\n##                            ##\n################################");

        File dir = new File("plugins/Markap-FJ-BALANCER");
        if (!dir.exists()) {
            boolean dirCreated = dir.mkdirs();
            if (!dirCreated) {
                logger.error("Failed to create directory");
                return;
            }
        }
        joinedPlayersFile = new File(dir, "joinedPlayers.yml");
        lastServerFile = new File(dir, "last-server-data.yml");

        reloadDataFromFile();
    }

    public File getJoinedPlayersFile() {
        return joinedPlayersFile;
    }

    public File getLastServerFile() {
        return lastServerFile;
    }

    public void setJoinedPlayers(Set<UUID> joinedPlayers) {
        Set<UUID> newSet = ConcurrentHashMap.newKeySet();
        if (joinedPlayers != null) {
            newSet.addAll(joinedPlayers);
        }
        this.joinedPlayers = newSet;
    }

    public void setLastServerData(Map<UUID, String> lastServerData) {
        Map<UUID, String> newMap = new ConcurrentHashMap<>();
        if (lastServerData != null) {
            newMap.putAll(lastServerData);
        }
        this.lastServerData = newMap;
    }

    public Map<UUID, String> getLastServerData() {
        return this.lastServerData;
    }

    public ProxyServer getServer() {
        return this.server;
    }

    public synchronized void saveLastServerData() {
        Path path = lastServerFile.toPath();
        try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            Yaml yaml = new Yaml();
            Map<String, String> serializable = new TreeMap<>();
            for (Map.Entry<UUID, String> entry : lastServerData.entrySet()) {
                serializable.put(entry.getKey().toString(), entry.getValue());
            }
            yaml.dump(serializable, writer);
            logger.info("Saved last server data to file.");
        } catch (IOException e) {
            logger.error("Failed to save last server data", e);
        }
    }

    public synchronized void saveJoinedPlayers() {
        if (!joinedPlayers.isEmpty()) {
            Path path = joinedPlayersFile.toPath();
            try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                Yaml yaml = new Yaml();
                List<String> serializable = joinedPlayers.stream()
                        .map(UUID::toString)
                        .sorted()
                        .toList();
                yaml.dump(serializable, writer);
                logger.info("Saved joined players to file");
            } catch (IOException e) {
                logger.error("Failed to save joined players", e);
            }
        }
    }

    @Subscribe
    public void onChooseInitialServer(PlayerChooseInitialServerEvent event) {
        Player player = event.getPlayer();

        if (!joinedPlayers.contains(player.getUniqueId())) {
            joinedPlayers.add(player.getUniqueId());
            logger.info("Player {} joined for the first time", player.getUsername());

            Optional<RegisteredServer> minPlayerServer = server.getAllServers().stream()
                    .min(Comparator.comparingInt(server2 -> server2.getPlayersConnected().size()));
            minPlayerServer.ifPresent(targetServer -> {
                event.setInitialServer(targetServer);
                logger.info("Player {} routed to least loaded server {}", player.getUsername(), targetServer.getServerInfo().getName());
            });

            server.getScheduler().buildTask(this, this::saveJoinedPlayers).schedule();
        } else {
            logger.info("Player {} has already joined before", player.getUsername());
            String lastServer = lastServerData.get(player.getUniqueId());
            if (lastServer != null) {
                Optional<RegisteredServer> registeredServer = server.getServer(lastServer);
                registeredServer.ifPresent(targetServer -> {
                    event.setInitialServer(targetServer);
                    logger.info("Player {} routed to last server {}", player.getUsername(), targetServer.getServerInfo().getName());
                });
            }
        }
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        player.getCurrentServer().ifPresent(serverConnection -> {
            String serverName = serverConnection.getServerInfo().getName();
            lastServerData.put(player.getUniqueId(), serverName);
            server.getScheduler().buildTask(this, this::saveLastServerData).schedule();
        });
    }

    public synchronized void reloadDataFromFile() {
        if (joinedPlayersFile.exists()) {
            try {
                Set<UUID> loadedPlayers = loadJoinedPlayers(joinedPlayersFile);
                Set<UUID> newJoined = ConcurrentHashMap.newKeySet();
                newJoined.addAll(loadedPlayers);
                this.joinedPlayers = newJoined;
                logger.info("[AUTO-RELOAD] Reloaded joined players from file");
            } catch (IOException | YAMLException e) {
                logger.error("[AUTO-RELOAD] Failed to reload joined players", e);
            }
        } else {
            logger.info("File does not exist, no players loaded");
        }

        if (lastServerFile.exists()) {
            try {
                Map<UUID, String> loadedLastServerData = loadLastServerData(lastServerFile);
                this.lastServerData = new ConcurrentHashMap<>(loadedLastServerData);
                logger.info("[AUTO-RELOAD] Reloaded last server data from file");
            } catch (IOException | YAMLException e) {
                logger.error("[AUTO-RELOAD] Failed to reload last server data", e);
            }
        } else {
            logger.info("File does not exist, no last server data loaded");
        }
    }

    private Set<UUID> loadJoinedPlayers(File file) throws IOException {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        String normalized = stripLegacyUuidTags(content);
        Yaml yaml = new Yaml();
        Object loaded = yaml.load(normalized);

        Set<UUID> result = new HashSet<>();
        if (loaded == null) {
            return result;
        }
        if (loaded instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                UUID uuid = parseUuid(value);
                if (uuid != null) {
                    result.add(uuid);
                } else {
                    logger.warn("Skipping invalid joined player UUID value: {}", value);
                }
            }
            return result;
        }

        if (loaded instanceof Map<?, ?> map) {
            for (Object key : map.keySet()) {
                UUID uuid = parseUuid(key);
                if (uuid != null) {
                    result.add(uuid);
                } else {
                    logger.warn("Skipping invalid joined player UUID key: {}", key);
                }
            }
            return result;
        }

        logger.warn("Unexpected joined players format in {}", file.getName());
        return result;
    }

    private Map<UUID, String> loadLastServerData(File file) throws IOException {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        String normalized = stripLegacyUuidTags(content);
        Yaml yaml = new Yaml();
        Object loaded = yaml.load(normalized);

        Map<UUID, String> result = new HashMap<>();
        if (loaded == null) {
            return result;
        }
        if (!(loaded instanceof Map<?, ?> map)) {
            logger.warn("Unexpected last server data format in {}", file.getName());
            return result;
        }

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            UUID uuid = parseUuid(entry.getKey());
            if (uuid == null) {
                logger.warn("Skipping invalid last server UUID key: {}", entry.getKey());
                continue;
            }
            if (entry.getValue() == null) {
                continue;
            }
            result.put(uuid, entry.getValue().toString());
        }
        return result;
    }

    private UUID parseUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString().trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String stripLegacyUuidTags(String content) {
        return content.replace("!!java.util.UUID ", "");
    }

    @Subscribe
    public void onPlayerJoin(PostLoginEvent event) {
        Player player = event.getPlayer();
        Locale locale = player.getEffectiveLocale();
        String message;

        if (locale != null && locale.getLanguage().equals("de")) {
            message = "&8[&6&lStyleLabor&8] &fHallo &e&l" + player.getUsername() + "&f, du kannst den Server mit &f&l/stylelabor-server <server>&f wechseln! Wenn das nicht funktioniert, benutze &6/server <server>&f!";
        } else {
            message = "&8[&6&lStyleLabor&8] &fHello &e&l" + player.getUsername() + "&f, you can change the server with &f&l/stylelabor-server <server>&f! When this isn't working, use &6/server <server>&f!";
        }

        // Schedule the message to be sent after 5 seconds using Velocity scheduler
        server.getScheduler().buildTask(this, () -> player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message)))
                .delay(5, TimeUnit.SECONDS)
                .schedule();
    }
}