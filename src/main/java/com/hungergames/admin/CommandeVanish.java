package com.hungergames.admin;

import com.hungergames.config.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class CommandeVanish implements CommandExecutor, TabCompleter, Listener {
    private final JavaPlugin plugin;
    private final Set<UUID> caches = new HashSet<>();

    public CommandeVanish(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("commun.cette_commande_doit_etre_executee_par"));
            return true;
        }

        if (estVanish(player)) {
            desactiver(player);
        } else {
            activer(player);
        }

        return true;
    }

    public boolean estVanish(Player player) {
        return caches.contains(player.getUniqueId());
    }

    public void activer(Player player) {
        if (!caches.add(player.getUniqueId())) {
            return;
        }

        for (Player autre : Bukkit.getOnlinePlayers()) {
            if (!autre.equals(player)) {
                autre.hidePlayer(plugin, player);
            }
        }
        player.sendMessage(Messages.get("commandevanish.vanish_active_tu_es_invisible_aux"));
    }

    public void desactiver(Player player) {
        if (!caches.remove(player.getUniqueId())) {
            return;
        }

        for (Player autre : Bukkit.getOnlinePlayers()) {
            autre.showPlayer(plugin, player);
        }
        player.sendMessage(Messages.get("commandevanish.vanish_desactive_tu_es_de_nouveau"));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (UUID uuid : caches) {
            Player admin = Bukkit.getPlayer(uuid);
            if (admin != null) {
                event.getPlayer().hidePlayer(plugin, admin);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        caches.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
