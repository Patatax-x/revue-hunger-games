package com.hungergames.admin;

import com.hungergames.config.Messages;
import com.hungergames.game.CommandeEnd;
import com.hungergames.game.FreezeManager;
import com.hungergames.game.KillTracker;
import com.hungergames.lobby.LobbyManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class CommandeMode implements CommandExecutor, Listener, TabCompleter {
    private static final String TITRE_GUI = Messages.get("commandemode.mode_de_partie");

    private final LobbyManager lobbyManager;
    private final CommandeGo commandeGo;
    private final KillTracker killTracker;
    private final CommandeEnd commandeEnd;
    private final FreezeManager freezeManager;

    public CommandeMode(LobbyManager lobbyManager, CommandeGo commandeGo, KillTracker killTracker, CommandeEnd commandeEnd, FreezeManager freezeManager) {
        this.lobbyManager = lobbyManager;
        this.commandeGo = commandeGo;
        this.killTracker = killTracker;
        this.commandeEnd = commandeEnd;
        this.freezeManager = freezeManager;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String @NonNull [] args) {
        if (args.length >= 2 && (args[0].equalsIgnoreCase("lobby") || args[0].equalsIgnoreCase("off"))) {
            if (killTracker.estPartieDemarree() || commandeEnd.isFinDePartieEnCours() || freezeManager.compteAReboursEnCours()) {
                sender.sendMessage(Messages.get("commandemode.impossible_une_partie_est_en_cours"));
                sender.sendMessage(Messages.get("commandemode.un_joueur_qui_doit_sortir_du"));
                return true;
            }

            Player cible = Bukkit.getPlayerExact(args[1]);
            if (cible == null) {
                sender.sendMessage(Messages.get("commun.joueur_introuvable", "args", args[1]));
                return true;
            }

            if (args[0].equalsIgnoreCase("lobby")) {
                lobbyManager.resynchroniserJoueurLobby(cible);
            } else {
                lobbyManager.resynchroniserJoueurOff(cible);
            }
            sender.sendMessage(Messages.get("commandemode.resynchronise", "cible", cible.getName(), "args", args[0].toLowerCase(Locale.ROOT)));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("commun.cette_commande_doit_etre_executee_par"));
            return true;
        }

        ouvrirMenu(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("lobby", "off");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("lobby") || args[0].equalsIgnoreCase("off"))) {
            List<String> noms = new ArrayList<>();
            for (Player joueur : Bukkit.getOnlinePlayers()) {
                noms.add(joueur.getName());
            }
            return noms;
        }
        return Collections.emptyList();
    }

    private void ouvrirMenu(Player player) {
        Inventory menu = Bukkit.createInventory(null, 9, TITRE_GUI);

        LobbyManager.EtatPartie etat = lobbyManager.getEtat();

        menu.setItem(2, creerItem(Material.BARRIER, Messages.get("commandemode.off_texte"), List.of(Messages.get("commandemode.rien_ne_se_passe")), etat == LobbyManager.EtatPartie.OFF));
        menu.setItem(4, creerItem(Material.GRASS_BLOCK, Messages.get("commandemode.lobby"), List.of(Messages.get("commandemode.distribue_les_objets_de_choix"), Messages.get("commandemode.equipe_classe"), Messages.get("commandemode.ramene_tout_le_monde_au_lobby")), etat == LobbyManager.EtatPartie.LOBBY));
        boolean fermetureActive = etat == LobbyManager.EtatPartie.FERMETURE;
        List<String> loreTnt = fermetureActive
                ? List.of(Messages.get("commandemode.reclique_pour_choisir_la_map"), Messages.get("commandemode.go"))
                : List.of(Messages.get("commandemode.ferme_le_lobby_attribution_auto"), Messages.get("commandemode.des_joueurs_incomplets"), Messages.get("commandemode.les_admins_choisissent_la_map_ensuite"));
        menu.setItem(6, creerItem(Material.TNT, Messages.get("commandemode.fermeture"), loreTnt, fermetureActive));

        player.openInventory(menu);
    }

    private ItemStack creerItem(Material material, String nom, List<String> lore, boolean actif) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(actif ? Messages.get("commandemode.actif", "nom", nom) : nom);
            meta.setLore(lore);
            if (actif) {
                meta.setEnchantmentGlintOverride(true);
            }
            item.setItemMeta(meta);
        }

        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!TITRE_GUI.equals(event.getView().getTitle())) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null) {
            return;
        }

        boolean partieEnCours = killTracker.estPartieDemarree() || commandeEnd.isFinDePartieEnCours() || freezeManager.compteAReboursEnCours();

        switch (item.getType()) {
            case BARRIER -> {
                if (partieEnCours) {
                    player.sendMessage(Messages.get("commandemode.impossible_une_partie_est_en_cours_2"));
                    return;
                }
                lobbyManager.desactiver();
                player.sendMessage(Messages.get("commandemode.mode_desactive"));
                player.closeInventory();
            }
            case GRASS_BLOCK -> {
                if (partieEnCours) {
                    player.sendMessage(Messages.get("commandemode.impossible_une_partie_est_en_cours_2"));
                    return;
                }
                lobbyManager.activerLobby();
                player.closeInventory();
            }
            case TNT -> {
                if (lobbyManager.isFermetureActive()) {
                    player.closeInventory();
                    commandeGo.ouvrirMenu(player);
                    return;
                }
                lobbyManager.activerFermeture();
                player.closeInventory();
            }
            default -> {
            }
        }
    }
}
