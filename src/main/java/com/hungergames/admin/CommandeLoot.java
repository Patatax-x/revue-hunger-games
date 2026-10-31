package com.hungergames.admin;

import com.hungergames.config.Messages;
import com.hungergames.config.Reglages;
import com.hungergames.loot.ChestCaptureTool;
import com.hungergames.loot.LootRegistry;
import com.hungergames.loot.Rarete;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CommandeLoot implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final LootRegistry lootRegistry;
    private final ChestCaptureTool captureTool;

    public CommandeLoot(JavaPlugin plugin, LootRegistry lootRegistry, ChestCaptureTool captureTool) {
        this.plugin = plugin;
        this.lootRegistry = lootRegistry;
        this.captureTool = captureTool;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("commun.cette_commande_doit_etre_executee_par"));
            return true;
        }

        if (args.length == 0) {
            captureTool.ouvrirChoixCible(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "outil" -> {
                player.getInventory().addItem(captureTool.creerItem());
                player.sendMessage(Messages.get("commandeloot.selectionneur_de_coffres_ajoute_ton_inventaire"));
            }
            case "vider" -> vider(player, args);
            case "recharger" -> {
                Reglages.charger(plugin);
                lootRegistry.charger();
                player.sendMessage(Messages.get("commandeloot.config_yml_et_chest_items_yml"));
            }
            case "defaut", "défaut", "reset" -> {
                lootRegistry.reinitialiserAuxDefauts();
                player.sendMessage(Messages.get("commandeloot.toutes_les_captures_ont_ete_retirees"));
                player.sendMessage(Messages.get("commandeloot.tout_ce_qui_avait_ete_capture"));
            }
            case "liste" -> liste(player);
            default -> envoyerAide(player);
        }

        return true;
    }

    private void vider(Player player, String[] args) {
        Rarete rarete = parseRarete(player, args);
        if (rarete == null) {
            return;
        }

        int maxVariantes = rarete.getNombreVariantes();

        if (args.length >= 3) {
            int variante;
            try {
                variante = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
                variante = 1;
            }

            if (variante < 1 || variante > maxVariantes) {
                player.sendMessage(Messages.get("commandeloot.variante_invalide_en", "args", args[2], "rarete", rarete.getAffichage(), "maxvariantes", maxVariantes, "maxvariantes2", maxVariantes));
                return;
            }

            int n = lootRegistry.vider(rarete, variante);
            player.sendMessage(Messages.get("commandeloot.pool_variante_vide_item_retire", "rarete", rarete.getAffichage(), "variante", variante, "n", n));
            return;
        }

        int total = 0;
        for (int variante = 1; variante <= maxVariantes; variante++) {
            total += lootRegistry.vider(rarete, variante);
        }
        player.sendMessage(Messages.get("commandeloot.pool_vide_variantes_item_retire", "rarete", rarete.getAffichage(), "maxvariantes", maxVariantes, "total", total));
    }

    private void liste(Player player) {
        player.sendMessage(Messages.get("commandeloot.pools_de_loot_captures"));
        for (Rarete rarete : Rarete.values()) {
            StringBuilder ligne = new StringBuilder(Messages.get("commandeloot.rarete", "rarete", rarete.getAffichage()));
            for (int variante = 1; variante <= rarete.getNombreVariantes(); variante++) {
                if (variante > 1) ligne.append(Messages.get("commandeloot.texte"));
                ligne.append(Messages.get("commandeloot.texte_2")).append(variante).append("=").append(lootRegistry.taille(rarete, variante));
            }
            player.sendMessage(ligne.toString());
        }
    }

    private Rarete parseRarete(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Messages.get("commandeloot.usage_loot_vider_variante", "nomsraretes", nomsRaretes()));
            return null;
        }

        try {
            return Rarete.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Messages.get("commandeloot.rarete_invalide_valeurs_possibles", "nomsraretes", nomsRaretes()));
            return null;
        }
    }

    private String nomsRaretes() {
        return Arrays.stream(Rarete.values()).map(Enum::name).collect(Collectors.joining("|"));
    }

    private void envoyerAide(Player player) {
        player.sendMessage(Messages.get("commandeloot.loot"));
        player.sendMessage(Messages.get("commandeloot.loot_ouvre_interface_de_choix_rarete"));
        player.sendMessage(Messages.get("commandeloot.loot_outil_donne_le_selectionneur_de"));
        player.sendMessage(Messages.get("commandeloot.loot_vider_rarete_variante_retire_une"));
        player.sendMessage(Messages.get("commandeloot.loot_recharger_recharge_chest_items_yml"));
        player.sendMessage(Messages.get("commandeloot.loot_defaut_retire_toutes_les_captures"));
        player.sendMessage(Messages.get("commandeloot.loot_liste_tailles_actuelles_des_pools"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("outil", "vider", "recharger", "defaut", "liste");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("vider")) {
            List<String> noms = new ArrayList<>();
            for (Rarete rarete : Rarete.values()) {
                noms.add(rarete.name());
            }
            return noms;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("vider")) {
            return List.of("1", "2", "3", "4");
        }
        return List.of();
    }
}
