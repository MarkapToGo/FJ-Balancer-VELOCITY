package de.stylelabor.dev.fjbalancervelocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("ALL")
@Plugin(
        id = "fj-balancer-velocity",
        name = "FJ-Balancer-VELOCITY",
        version = "0.1"
)
public class FJ_Balancer_VELOCITY {

    @Inject
    private Logger logger;

    @Inject
    private ProxyServer server;

    private Set<UUID> joinedPlayers;
    private Map<UUID, String> lastServerData;
    private File joinedPlayersFile = new File("plugins/Markap-FJ-BALANCER/joinedPlayers.yml");
    private File lastServerFile = new File("plugins/Markap-FJ-BALANCER/last-server-data.yml");

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {

        // Unregister the default /server command
        // server.getCommandManager().unregister("server");

        // Register custom commands
        server.getCommandManager().register("fjv-reload", new ReloadCommand(this, logger));
        server.getCommandManager().register("stylelabor-server", new StylelaborServerCommand(this, logger));
        server.getCommandManager().register("switch-server", new StylelaborServerCommand(this, logger));
        // server.getCommandManager().register("server", new StylelaborServerCommand(this, logger));

        // Unregister default server connection event
        // server.getEventManager().unregisterListeners(this);

        logger.info("\n################################\n##                            ##\n##   FJ Balancer [Velocity]   ##\n##      coded by Markap       ##\n##                            ##\n################################");

        // Load joined players and last server data
        loadJoinedPlayers();
        loadLastServerData();
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String playerName = player.getUsername();

        // Cancel the default server connection
        event.setResult(ServerPreConnectEvent.ServerResult.denied());

        // Handle server connection logic in your plugin
        String lastServer = lastServerData.get(playerId);
        if (lastServer != null) {
            Optional<RegisteredServer> registeredServer = server.getServer(lastServer);
            if (registeredServer.isPresent()) {
                RegisteredServer targetServer = registeredServer.get();
                player.createConnectionRequest(targetServer).fireAndForget();
                logger.info("Player {} sent to last server {}", playerName, targetServer.getServerInfo().getName());
            } else {
                logger.info("Last server {} for player {} not found", lastServer, playerName);
            }
        } else {
            logger.info("No last server data found for player {}", playerName);
        }
    }

    private void loadJoinedPlayers() {
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

        joinedPlayers = new HashSet<>(); // Initialize joinedPlayers to an empty set
        lastServerData = new HashMap<>(); // Initialize lastServerData to an empty map

        if (joinedPlayersFile.exists()) {
            try (FileReader reader = new FileReader(joinedPlayersFile)) {
                Yaml yaml = new Yaml();
                Set<UUID> loadedPlayers = yaml.load(reader);
                if (loadedPlayers != null) {
                    joinedPlayers.addAll(loadedPlayers);
                }
                logger.info("Loaded joined players from file");
            } catch (IOException e) {
                logger.info("Failed to load joined players", e);
            }
        } else {
            logger.info("File does not exist, no players loaded");
        }
    }

    private void loadLastServerData() {
        if (lastServerFile.exists()) {
            try (FileReader reader = new FileReader(lastServerFile)) {
                Yaml yaml = new Yaml();
                Map<UUID, String> loadedLastServerData = yaml.load(reader);
                if (loadedLastServerData != null) {
                    lastServerData.putAll(loadedLastServerData);
                    logger.info("Loaded last server data from file");
                } else {
                    logger.info("Loaded last server data is null");
                }
            } catch (IOException e) {
                logger.info("Failed to load last server data", e);
            }
        } else {
            logger.info("File does not exist, no last server data loaded");
        }

        // Additional logging to verify the content of lastServerData
        if (lastServerData.isEmpty()) {
            logger.info("lastServerData is empty after loading from file");
        } else {
            logger.info("lastServerData contains {} entries after loading from file", lastServerData.size());
        }
    }

    public File getJoinedPlayersFile() {
        return joinedPlayersFile;
    }

    public File getLastServerFile() {
        return lastServerFile;
    }

    public void setJoinedPlayers(Set<UUID> joinedPlayers) {
        this.joinedPlayers = joinedPlayers;
    }

    public void setLastServerData(Map<UUID, String> lastServerData) {
        this.lastServerData = lastServerData;
    }

    public Map<UUID, String> getLastServerData() {
        return this.lastServerData;
    }

    public ProxyServer getServer() {
        return this.server;
    }

    private int maxRetries = 3;
    private int retryDelaySeconds = 5;
    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public int getRetryDelaySeconds() {
        return retryDelaySeconds;
    }

    public void setRetryDelaySeconds(int retryDelaySeconds) {
        this.retryDelaySeconds = retryDelaySeconds;
    }

    public void saveLastServerData() {
        try (FileWriter writer = new FileWriter(lastServerFile)) {
            Yaml yaml = new Yaml();
            yaml.dump(lastServerData, writer);
            logger.info("Saved last server data to file.");
        } catch (IOException e) {
            logger.info("Failed to save last server data", e);
        }
    }

    private void saveJoinedPlayers() {
        try (FileWriter writer = new FileWriter(joinedPlayersFile)) {
            Yaml yaml = new Yaml();
            yaml.dump(joinedPlayers, writer);
            logger.info("Saved joined players to file");
        } catch (IOException e) {
            logger.info("Failed to save joined players", e);
        }
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        player.getCurrentServer().ifPresent(serverConnection -> {
            String serverName = serverConnection.getServerInfo().getName();
            lastServerData.put(player.getUniqueId(), serverName);
            try (FileWriter writer = new FileWriter(lastServerFile)) {
                Yaml yaml = new Yaml();
                yaml.dump(lastServerData, writer);
                //noinspection LoggingSimilarMessage
                logger.info("Saved last server data to file. - onPlayerDisconnect");
            } catch (IOException e) {
                logger.info("Failed to save last server data", e);
            }
        });
    }


    @SuppressWarnings("LoggingSimilarMessage")
    @Subscribe
    public void onPlayerJoin(PostLoginEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String playerName = player.getUsername();

        // Send a welcome message to the player
        player.sendMessage(Component.text("Welcome " + playerName + "! You can switch servers using /stylelabor-server <server>."));

        if (!joinedPlayers.contains(playerId)) {
            joinedPlayers.add(playerId);
            logger.info("Player {} joined for the first time", playerName);

            Optional<RegisteredServer> minPlayerServer = server.getAllServers().stream()
                    .min(Comparator.comparingInt(server2 -> server2.getPlayersConnected().size()));
            if (minPlayerServer.isPresent()) {
                RegisteredServer targetServer = minPlayerServer.get();
                scheduleServerConnection(player, targetServer, getMaxRetries(), getRetryDelaySeconds());
            } else {
                logger.info("No available servers to send player {}", playerName);
            }

            saveJoinedPlayers();
        } else {
            logger.info("Player {} has already joined before", playerName);
            String lastServer = lastServerData.get(playerId);
            if (lastServer != null) {
                Optional<RegisteredServer> registeredServer = server.getServer(lastServer);
                if (registeredServer.isPresent()) {
                    RegisteredServer targetServer = registeredServer.get();
                    scheduleServerConnection(player, targetServer, getMaxRetries(), getRetryDelaySeconds());
                } else {
                    logger.info("Last server {} for player {} not found, sending to server with minimum players", lastServer, playerName);
                    Optional<RegisteredServer> minPlayerServer = server.getAllServers().stream()
                            .min(Comparator.comparingInt(server2 -> server2.getPlayersConnected().size()));
                    if (minPlayerServer.isPresent()) {
                        RegisteredServer targetServer = minPlayerServer.get();
                        scheduleServerConnection(player, targetServer, getMaxRetries(), getRetryDelaySeconds());
                    } else {
                        logger.info("No available servers to send player {}", playerName);
                    }
                }
            } else {
                logger.info("No last server data found for player {}, sending to server with minimum players", playerName);
                Optional<RegisteredServer> minPlayerServer = server.getAllServers().stream()
                        .min(Comparator.comparingInt(server2 -> server2.getPlayersConnected().size()));
                if (minPlayerServer.isPresent()) {
                    RegisteredServer targetServer = minPlayerServer.get();
                    scheduleServerConnection(player, targetServer, getMaxRetries(), getRetryDelaySeconds());
                } else {
                    logger.info("No available servers to send player {}", playerName);
                }
            }
        }
    }

    private void scheduleServerConnection(Player player, RegisteredServer targetServer, int retries, int delaySeconds) {
        server.getScheduler().buildTask(this, () -> {
            try {
                player.createConnectionRequest(targetServer).fireAndForget();
                logger.info("Player {} sent to server {}", player.getUsername(), targetServer.getServerInfo().getName());
            } catch (Exception e) {
                logger.error("Failed to send player {} to server {}, retries left: {}", player.getUsername(), targetServer.getServerInfo().getName(), retries, e);
                if (retries > 0) {
                    scheduleServerConnection(player, targetServer, retries - 1, delaySeconds);
                } else {
                    logger.error("Exhausted all retries for player {} to server {}", player.getUsername(), targetServer.getServerInfo().getName());
                }
            }
        }).delay(delaySeconds, TimeUnit.SECONDS).schedule();
    }
}