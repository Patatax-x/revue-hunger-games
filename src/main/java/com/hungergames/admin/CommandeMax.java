package com.hungergames.admin;

import com.hungergames.config.Messages;
import com.hungergames.game.KillTracker;
import com.hungergames.map.BorderManager;
import com.hungergames.map.GameMap;
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

public class CommandeMax implements CommandExecutor, TabCompleter {
    private final KillTracker killTracker;
    private final MapManager mapManager;
    private final BorderManager borderManager;

    public CommandeMax(KillTracker killTracker, MapManager mapManager, BorderManager borderManager) {
        this.killTracker = killTracker;
        this.mapManager = mapManager;
        this.borderManager = borderManager;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String @NonNull [] args) {
        if (!killTracker.estPartieDemarree()) {
            sender.sendMessage(Messages.get("commandemax.erreur_max_est_utilisable_que_pendant"));
            return true;
        }

        GameMap map = mapManager.getMapActuelle();
        World monde = Bukkit.getWorld(map.getCle());
        if (monde == null) {
            sender.sendMessage(Messages.get("commun.erreur_map_introuvable"));
            return true;
        }

        borderManager.activerVitesseMax(monde, map);
        Bukkit.broadcastMessage(Messages.get("commandemax.la_zone_de_jeu_se_retrecit"));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
