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

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
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

        if (joinedPlayersFile.exists()) {
            try (FileReader reader = new FileReader(joinedPlayersFile)) {
                Yaml yaml = new Yaml();
                Set<UUID> loadedPlayers = yaml.load(reader);
                if (loadedPlayers != null) {
                    joinedPlayers.addAll(loadedPlayers);
                }
                logger.info("Loaded joined players from file");
            } catch (IOException e) {
                logger.error("Failed to load joined players", e);
            }
        } else {
            logger.info("File does not exist, no players loaded");
        }

        if (lastServerFile.exists()) {
            try (FileReader reader = new FileReader(lastServerFile)) {
                Yaml yaml = new Yaml();
                Map<UUID, String> loadedLastServerData = yaml.load(reader);
                if (loadedLastServerData != null) {
                    lastServerData.putAll(loadedLastServerData);
                }
                logger.info("Loaded last server data from file");
            } catch (IOException e) {
                logger.error("Failed to load last server data", e);
            }
        } else {
            logger.info("File does not exist, no last server data loaded");
        }
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
        try (FileWriter writer = new FileWriter(lastServerFile)) {
            Yaml yaml = new Yaml();
            yaml.dump(new HashMap<>(lastServerData), writer);
            logger.info("Saved last server data to file.");
        } catch (IOException e) {
            logger.error("Failed to save last server data", e);
        }
    }

    public synchronized void saveJoinedPlayers() {
        if (!joinedPlayers.isEmpty()) {
            try (FileWriter writer = new FileWriter(joinedPlayersFile)) {
                Yaml yaml = new Yaml();
                yaml.dump(new ArrayList<>(joinedPlayers), writer);
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

    private synchronized void reloadDataFromFile() {
        if (joinedPlayersFile.exists() && lastServerFile.exists()) {
            try (FileReader reader = new FileReader(joinedPlayersFile)) {
                Yaml yaml = new Yaml();
                Set<UUID> loadedPlayers = yaml.load(reader);
                if (loadedPlayers != null) {
                    Set<UUID> newJoined = ConcurrentHashMap.newKeySet();
                    newJoined.addAll(loadedPlayers);
                    this.joinedPlayers = newJoined;
                }
                logger.info("[AUTO-RELOAD] Reloaded joined players from file");
            } catch (IOException e) {
                logger.error("[AUTO-RELOAD] Failed to reload joined players", e);
            }

            try (FileReader reader = new FileReader(lastServerFile)) {
                Yaml yaml = new Yaml();
                Map<UUID, String> loadedLastServerData = yaml.load(reader);
                if (loadedLastServerData != null) {
                    Map<UUID, String> newLastServerData = new ConcurrentHashMap<>(loadedLastServerData);
                    this.lastServerData = newLastServerData;
                }
                logger.info("[AUTO-RELOAD] Reloaded last server data from file");
            } catch (IOException e) {
                logger.error("[AUTO-RELOAD] Failed to reload last server data", e);
            }
        } else {
            logger.warn("[AUTO-RELOAD] Skipped reloading because one or both files do not exist");
        }
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