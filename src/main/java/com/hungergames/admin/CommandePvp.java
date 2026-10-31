package com.hungergames.admin;

import com.hungergames.config.Messages;
import com.hungergames.map.MapManager;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.List;

public class CommandePvp implements CommandExecutor, TabCompleter {
    private final MapManager mapManager;

    public CommandePvp(MapManager mapManager) {
        this.mapManager = mapManager;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String @NonNull [] args) {
        World monde = Bukkit.getWorld(mapManager.getMapActuelle().getCle());
        if (monde == null) {
            sender.sendMessage(Messages.get("commun.erreur_map_introuvable"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(monde.getPVP()
                    ? Messages.get("commandepvp.le_pvp_est_actuellement_active_sur", "mapactuelle", mapManager.getMapActuelle().getAffichage())
                    : Messages.get("commandepvp.le_pvp_est_actuellement_desactive_sur", "mapactuelle", mapManager.getMapActuelle().getAffichage()));
            sender.sendMessage(Messages.get("commandepvp.usage_pvp_on_off"));
            return true;
        }

        if (args[0].equalsIgnoreCase("on")) {
            monde.setPVP(true);
            Bukkit.broadcastMessage(Messages.get("commun.pvp_reactive_par_un_admin"));
        } else if (args[0].equalsIgnoreCase("off")) {
            monde.setPVP(false);
            Bukkit.broadcastMessage(Messages.get("commun.pvp_desactive_par_un_admin"));
        } else {
            sender.sendMessage(Messages.get("commandepvp.usage_pvp_on_off_2"));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("on", "off");
        }
        return Collections.emptyList();
    }
}
