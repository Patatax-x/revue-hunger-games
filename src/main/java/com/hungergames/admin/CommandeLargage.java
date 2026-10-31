package com.hungergames.admin;

import com.hungergames.config.Messages;
import com.hungergames.game.KillTracker;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.List;

public class CommandeLargage implements CommandExecutor, TabCompleter {
    private final KillTracker killTracker;
    private final MatchAdminKit matchAdminKit;

    public CommandeLargage(KillTracker killTracker, MatchAdminKit matchAdminKit) {
        this.killTracker = killTracker;
        this.matchAdminKit = matchAdminKit;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("commun.cette_commande_doit_etre_executee_par"));
            return true;
        }

        if (!killTracker.estPartieDemarree()) {
            player.sendMessage(Messages.get("commandelargage.erreur_largage_est_utilisable_que_pendant"));
            return true;
        }

        matchAdminKit.ouvrirChoixLargage(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
