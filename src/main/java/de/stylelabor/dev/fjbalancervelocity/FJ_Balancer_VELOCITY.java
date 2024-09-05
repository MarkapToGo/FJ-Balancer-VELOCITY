package de.stylelabor.dev.fjbalancervelocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Plugin(
        id = "fj-balancer-velocity",
        name = "FJ-Balancer-VELOCITY",
        version = "0.2"
)
public class FJ_Balancer_VELOCITY {

    @Inject
    private Logger logger;

    @Inject
    private ProxyServer server;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private Set<UUID> joinedPlayers;
    private Map<UUID, String> lastServerData;
    private File joinedPlayersFile = new File("plugins/Markap-FJ-BALANCER/joinedPlayers.yml");
    private File lastServerFile = new File("plugins/Markap-FJ-BALANCER/last-server-data.yml");

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {

        // Schedule the task to reload data every minute
        scheduler.scheduleAtFixedRate(this::reloadDataFromFile, 0, 60, TimeUnit.SECONDS);

        server.getCommandManager().register("fjv-reload", new ReloadCommand(this, logger));
        server.getCommandManager().register("stylelabor-server", new StylelaborServerCommand(this, logger));
        server.getCommandManager().register("switch-server", new StylelaborServerCommand(this, logger));

        logger.info("\n################################\n##                            ##\n##   FJ Balancer [Velocity]   ##\n##      coded by Markap       ##\n##                            ##\n################################");

        File dir = new File("plugins/Markap-FJ-BALANCER");
        if (!dir.exists()) {
            boolean dirCreated = dir.mkdirs(); // This creates the directory if it doesn't exist
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

    public void saveLastServerData() {
        try (FileWriter writer = new FileWriter(lastServerFile)) {
            Yaml yaml = new Yaml();
            yaml.dump(lastServerData, writer);
            logger.info("Saved last server data to file.");
        } catch (IOException e) {
            logger.error("Failed to save last server data", e);
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
                logger.error("Failed to save last server data", e);
            }
        });
    }

    private void reloadDataFromFile() {
        if (joinedPlayersFile.exists() && lastServerFile.exists()) {
            try (FileReader reader = new FileReader(joinedPlayersFile)) {
                Yaml yaml = new Yaml();
                Set<UUID> loadedPlayers = yaml.load(reader);
                if (loadedPlayers != null) {
                    joinedPlayers = new HashSet<>(loadedPlayers);
                }
                logger.info("[AUTO-RELOAD] Reloaded joined players from file");
            } catch (IOException e) {
                logger.error("[AUTO-RELOAD] Failed to reload joined players", e);
            }

            try (FileReader reader = new FileReader(lastServerFile)) {
                Yaml yaml = new Yaml();
                Map<UUID, String> loadedLastServerData = yaml.load(reader);
                if (loadedLastServerData != null) {
                    lastServerData = new HashMap<>(loadedLastServerData);
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

        // Schedule the message to be sent after 5 seconds
        scheduler.schedule(() -> player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message)), 5, TimeUnit.SECONDS);

        balancePlayerOnFirstJoin(player);
    }

    private void balancePlayerOnFirstJoin(Player player) {
        if (!joinedPlayers.contains(player.getUniqueId())) {
            joinedPlayers.add(player.getUniqueId());
            logger.info("Player {} joined for the first time", player.getUsername());

            Optional<RegisteredServer> minPlayerServer = server.getAllServers().stream()
                    .min(Comparator.comparingInt(server2 -> server2.getPlayersConnected().size()));
            minPlayerServer.ifPresent(server -> {
                if (player.getCurrentServer().isEmpty()) {
                    player.createConnectionRequest(server).fireAndForget();
                    logger.info("Player {} sent to server {}", player.getUsername(), server.getServerInfo().getName());
                } else {
                    logger.info("Player {} is already connected to a server", player.getUsername());
                }
            });

            saveJoinedPlayers();
        } else {
            logger.info("Player {} has already joined before", player.getUsername());
            String lastServer = lastServerData.get(player.getUniqueId());
            if (lastServer != null) {
                Optional<RegisteredServer> registeredServer = server.getServer(lastServer);
                registeredServer.ifPresent(server -> {
                    if (player.getCurrentServer().isPresent()) {
                        player.disconnect(Component.text("Transferring you to " + lastServer + "! Please reconnect."));
                        retryConnection(player, server, 10, 5); // Retry 5 times with a 10-second delay
                    } else {
                        player.createConnectionRequest(server).fireAndForget();
                        logger.info("Player {} sent to last server {}", player.getUsername(), server.getServerInfo().getName());
                    }
                });
            }
        }
    }

    private void retryConnection(Player player, RegisteredServer server, int delaySeconds, int maxRetries) {
        scheduler.schedule(() -> {
            if (maxRetries > 0) {
                player.createConnectionRequest(server).connectWithIndication().thenAccept(success -> {
                    if (!success) {
                        logger.warn("Failed to transfer player {} to server {}. Retrying... ({} attempts left)", player.getUsername(), server.getServerInfo().getName(), maxRetries - 1);
                        player.sendMessage(Component.text("Retrying transfer to " + server.getServerInfo().getName() + "... (" + (maxRetries - 1) + " attempts left)"));
                        retryConnection(player, server, delaySeconds, maxRetries - 1);
                    } else {
                        logger.info("Player {} successfully transferred to server {}", player.getUsername(), server.getServerInfo().getName());
                        player.sendMessage(Component.text("Successfully transferred to " + server.getServerInfo().getName() + "!"));
                    }
                });
            } else {
                logger.error("Failed to transfer player {} to server {} after multiple attempts.", player.getUsername(), server.getServerInfo().getName());
                player.sendMessage(Component.text("Failed to transfer you to " + server.getServerInfo().getName() + " after multiple attempts."));
            }
        }, delaySeconds, TimeUnit.SECONDS);
    }

    private void saveJoinedPlayers() {
        if (!joinedPlayers.isEmpty()) {
            try (FileWriter writer = new FileWriter(joinedPlayersFile)) {
                Yaml yaml = new Yaml();
                yaml.dump(joinedPlayers, writer);
                logger.info("Saved joined players to file");
            } catch (IOException e) {
                logger.error("Failed to save joined players", e);
            }
        }
    }
}