package com.hungergames.admin;

import com.hungergames.config.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.List;

public class CommandePNJ implements CommandExecutor, Listener, TabCompleter {
    private static final String TAG_PNJ = "hg_pnj_mannequin";

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String @NonNull [] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("kill")) {
            return tuerTousLesPnj(sender);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("commun.cette_commande_doit_etre_executee_par"));
            return true;
        }

        Location location = player.getLocation();

        Villager pnj = player.getWorld().spawn(location, Villager.class);

        pnj.setAI(false);
        pnj.setInvulnerable(false);
        pnj.setSilent(false);
        pnj.setCollidable(true);
        pnj.setPersistent(true);
        pnj.setRemoveWhenFarAway(false);
        pnj.setCustomName(Messages.get("commandepnj.mannequin_entrainement"));
        pnj.setCustomNameVisible(true);
        pnj.addScoreboardTag(TAG_PNJ);

        player.sendMessage(Messages.get("commandepnj.mannequin_entrainement_cree_tu_peux_tester"));

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("kill");
        }
        return Collections.emptyList();
    }

    private boolean tuerTousLesPnj(CommandSender sender) {
        int compteur = 0;

        for (World monde : Bukkit.getWorlds()) {
            for (Villager villager : monde.getEntitiesByClass(Villager.class)) {
                if (villager.getScoreboardTags().contains(TAG_PNJ)) {
                    villager.remove();
                    compteur++;
                }
            }
        }

        sender.sendMessage(Messages.get("commandepnj.mannequin_entrainement_supprime", "compteur", compteur));
        return true;
    }

    @EventHandler
    public void onDegatsMannequin(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }

        if (!villager.getScoreboardTags().contains(TAG_PNJ)) {
            return;
        }

        double vieRestante = villager.getHealth() - event.getFinalDamage();

        if (vieRestante <= 0) {
            event.setCancelled(true);
            villager.setHealth(villager.getMaxHealth());
        }
    }
}
