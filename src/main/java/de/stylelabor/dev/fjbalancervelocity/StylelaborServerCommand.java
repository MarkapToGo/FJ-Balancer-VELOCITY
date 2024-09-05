package de.stylelabor.dev.fjbalancervelocity;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class StylelaborServerCommand implements SimpleCommand {

    private final FJ_Balancer_VELOCITY plugin;
    private final Logger logger;

    public StylelaborServerCommand(FJ_Balancer_VELOCITY plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    @Override
    public void execute(Invocation invocation) {
        if (invocation.arguments().length != 1) {
            invocation.source().sendMessage(Component.text("Usage: /stylelabor-server <server>"));
            return;
        }

        String serverName = invocation.arguments()[0];
        Optional<RegisteredServer> serverOptional = plugin.getServer().getServer(serverName);
        if (serverOptional.isEmpty()) {
            invocation.source().sendMessage(Component.text("Server " + serverName + " not found."));
            return;
        }

        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Component.text("This command can only be run by a player."));
            return;
        }

        // Check if the player is already connecting to a server
        if (player.getCurrentServer().isPresent() && player.getCurrentServer().get().getServerInfo().getName().equals(serverName)) {
            invocation.source().sendMessage(Component.text("You are already connected to " + serverName + "."));
            return;
        }

        // Log the command usage
        logger.info("Player {} is switching to server {}", player.getUsername(), serverName);

        // Schedule a task to save the last server data after a delay
        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            plugin.getLastServerData().put(player.getUniqueId(), serverName);
            plugin.saveLastServerData();
        }).delay(3, TimeUnit.SECONDS).schedule();

        // Check the backend server version
        serverOptional.get().ping().thenAccept(ping -> {
            String version = ping.getVersion().getName();
            if (version.contains("Neoforge") && version.contains("1.21")) {
                // Use Velocity send command
                player.createConnectionRequest(serverOptional.get()).fireAndForget();
                sendMessage(player, "Successfully transferred to " + serverName + "!");
            } else {
                // Disconnect the player and send them to the new server
                player.createConnectionRequest(serverOptional.get()).connectWithIndication().thenAccept(success -> {
                    if (success) {
                        sendMessage(player, "Successfully transferred to " + serverName + "!");
                    } else {
                        sendMessage(player, "Failed to transfer to " + serverName + ".");
                    }
                });
            }
        }).exceptionally(throwable -> {
            logger.error("Failed to ping server {} to get version", serverName, throwable);
            sendMessage(player, "Failed to get server version for " + serverName + ".");
            return null;
        });
    }

    private void sendMessage(Player player, String message) {
        Locale locale = player.getEffectiveLocale();
        if (locale != null && locale.getLanguage().equals("de")) {
            // Translate the message to German
            if (message.contains("Successfully transferred to")) {
                message = "Erfolgreich zu " + message.split("to ")[1] + " gewechselt!";
            } else if (message.contains("Failed to transfer to")) {
                message = "Fehler beim Wechseln zu " + message.split("to ")[1] + ".";
            } else if (message.contains("Failed to get server version for")) {
                message = "Fehler beim Abrufen der Serverversion für " + message.split("for ")[1] + ".";
            }
        }
        player.sendMessage(Component.text(message));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String currentInput = invocation.arguments().length > 0 ? invocation.arguments()[0] : "";
        return plugin.getServer().getAllServers().stream()
                .map(RegisteredServer::getServerInfo)
                .map(ServerInfo::getName)
                .filter(name -> name.startsWith(currentInput))
                .collect(Collectors.toList());
    }
}