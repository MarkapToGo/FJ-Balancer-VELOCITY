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

        // Check if the player is already connected to this server
        if (player.getCurrentServer().isPresent() && player.getCurrentServer().get().getServerInfo().getName().equals(serverName)) {
            invocation.source().sendMessage(Component.text("You are already connected to " + serverName + "."));
            return;
        }

        // Log the command usage
        logger.info("Player {} is switching to server {}", player.getUsername(), serverName);

        RegisteredServer targetServer = serverOptional.get();

        // Check the backend server version
        targetServer.ping().thenAccept(ping -> {
            String version = ping.getVersion().getName();
            if (version.contains("Neoforge") && version.contains("1.21")) {
                // Use Velocity send command
                player.createConnectionRequest(targetServer).fireAndForget();
                updateAndSaveLastServer(player, serverName);
                sendTransferSuccess(player, serverName);
            } else {
                player.createConnectionRequest(targetServer).connectWithIndication().thenAccept(success -> {
                    if (success) {
                        updateAndSaveLastServer(player, serverName);
                        sendTransferSuccess(player, serverName);
                    } else {
                        sendTransferFailure(player, serverName);
                    }
                });
            }
        }).exceptionally(throwable -> {
            logger.error("Failed to ping server {} to get version", serverName, throwable);
            sendPingFailure(player, serverName);
            return null;
        });
    }

    private void updateAndSaveLastServer(Player player, String serverName) {
        plugin.getLastServerData().put(player.getUniqueId(), serverName);
        plugin.getServer().getScheduler().buildTask(plugin, plugin::saveLastServerData).schedule();
    }

    private void sendTransferSuccess(Player player, String serverName) {
        Locale locale = player.getEffectiveLocale();
        String message = (locale != null && locale.getLanguage().equals("de"))
                ? "Erfolgreich zu " + serverName + " gewechselt!"
                : "Successfully transferred to " + serverName + "!";
        player.sendMessage(Component.text(message));
    }

    private void sendTransferFailure(Player player, String serverName) {
        Locale locale = player.getEffectiveLocale();
        String message = (locale != null && locale.getLanguage().equals("de"))
                ? "Fehler beim Wechseln zu " + serverName + "."
                : "Failed to transfer to " + serverName + ".";
        player.sendMessage(Component.text(message));
    }

    private void sendPingFailure(Player player, String serverName) {
        Locale locale = player.getEffectiveLocale();
        String message = (locale != null && locale.getLanguage().equals("de"))
                ? "Fehler beim Abrufen der Serverversion für " + serverName + "."
                : "Failed to get server version for " + serverName + ".";
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