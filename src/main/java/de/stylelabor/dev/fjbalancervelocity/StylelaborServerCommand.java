package de.stylelabor.dev.fjbalancervelocity;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class StylelaborServerCommand implements SimpleCommand {

    private final FJ_Balancer_VELOCITY plugin;

    public StylelaborServerCommand(FJ_Balancer_VELOCITY plugin, Logger ignoredLogger) {
        this.plugin = plugin;
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

        player.disconnect(Component.text("Transferring you to " + serverName + "! Please reconnect."));

        // Schedule a task to save the last server data after a delay
        plugin.getServer().getScheduler().buildTask(plugin, () -> {
            plugin.getLastServerData().put(player.getUniqueId(), serverName);
            plugin.saveLastServerData();
        }).delay(3, TimeUnit.SECONDS).schedule();
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