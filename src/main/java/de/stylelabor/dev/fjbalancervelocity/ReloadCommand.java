package de.stylelabor.dev.fjbalancervelocity;

import com.velocitypowered.api.command.SimpleCommand;
import org.slf4j.Logger;

public class ReloadCommand implements SimpleCommand {

    private final FJ_Balancer_VELOCITY plugin;
    private final Logger logger;

    public ReloadCommand(FJ_Balancer_VELOCITY plugin, Logger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    @Override
    public void execute(Invocation invocation) {
        plugin.reloadDataFromFile();
        logger.info("Reloaded joined players and last server data from file");
    }
}