package com.hungergames.admin;

import com.hungergames.classe.Classe;
import com.hungergames.classe.ClasseManager;
import com.hungergames.config.Messages;
import com.hungergames.role.RoleManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MenuClasse implements CommandExecutor, Listener, TabCompleter {
    private static final String TITRE_GUI = Messages.get("menuclasse.choix_de_la_classe");

    private static final Classe[] SLOTS = {
            Classe.BOUCLIER,
            Classe.VITESSE,
            Classe.INVISIBLE,
            Classe.FEU,
            Classe.LOUP_GAROU
    };

    private static final int[] EMPLACEMENTS = {2, 3, 4, 5, 6};

    // openBook n'émet aucun événement fiable à la fermeture : réouverture après un délai fixe.
    private static final long DELAI_REOUVERTURE_APRES_LIVRE = 40L;

    private final JavaPlugin plugin;
    private final ClasseManager classeManager;
    private final RoleManager roleManager;

    private final Set<UUID> joueursDansLeMenu = new HashSet<>();

    private final Set<UUID> menuForce = new HashSet<>();

    private final Set<UUID> enPreviewLivre = new HashSet<>();

    private boolean choixAutorise = false;

    public MenuClasse(JavaPlugin plugin, ClasseManager classeManager, RoleManager roleManager) {
        this.plugin = plugin;
        this.classeManager = classeManager;
        this.roleManager = roleManager;
        demarrerSurveillance();
    }

    private void demarrerSurveillance() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!choixAutorise || joueursDansLeMenu.isEmpty()) {
                return;
            }

            for (UUID uuid : new HashSet<>(joueursDansLeMenu)) {
                if (enPreviewLivre.contains(uuid)) {
                    continue;
                }
                if (!menuForce.contains(uuid)) {
                    continue;
                }

                Player player = Bukkit.getPlayer(uuid);

                if (player == null || !player.isOnline()) {
                    continue;
                }

                if (classeManager.getClasse(player).isPresent()) {
                    joueursDansLeMenu.remove(uuid);
                    continue;
                }

                // CRAFTING = aucun GUI ouvert. "Différent de CHEST" rouvrirait le menu par-dessus une enclume.
                if (player.getOpenInventory().getTopInventory().getType() == InventoryType.CRAFTING) {
                    ouvrirMenu(player);
                }
            }
        }, 5L, 5L);
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("leave")) {
            return gererLeave(sender, args);
        }

        if (!(sender instanceof Player)) {
            return true;
        }

        ouvrirMenuATousLesJoueurs();

        return true;
    }

    private boolean gererLeave(CommandSender sender, String[] args) {
        Player cible;
        if (args.length >= 2) {
            cible = Bukkit.getPlayer(args[1]);
            if (cible == null) {
                sender.sendMessage(Messages.get("commun.joueur_introuvable", "args", args[1]));
                return true;
            }
        } else if (sender instanceof Player joueur) {
            cible = joueur;
        } else {
            sender.sendMessage(Messages.get("menuclasse.precise_un_joueur_classe_leave_joueur"));
            return true;
        }

        if (classeManager.getClasse(cible).isEmpty()) {
            sender.sendMessage(Messages.get("menuclasse.pas_de_classe", "cible", cible.getName()));
            return true;
        }

        classeManager.retirerClasse(cible);
        sender.sendMessage(Messages.get("menuclasse.classe_retiree_pour", "cible", cible.getName()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("leave");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("leave")) {
            List<String> noms = new ArrayList<>();
            for (Player joueur : Bukkit.getOnlinePlayers()) {
                noms.add(joueur.getName());
            }
            return noms;
        }
        return Collections.emptyList();
    }

    public void ouvrirMenuATousLesJoueurs() {
        choixAutorise = true;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (roleManager.estExclu(player)) {
                continue;
            }

            if (classeManager.getClasse(player).isPresent()) {
                continue;
            }

            joueursDansLeMenu.add(player.getUniqueId());
            ouvrirMenu(player);
        }
    }

    public void ouvrirMenuPourJoueur(Player player) {
        ouvrirMenuPourJoueur(player, false);
    }

    public void ouvrirMenuPourJoueurForce(Player player) {
        ouvrirMenuPourJoueur(player, true);
    }

    private void ouvrirMenuPourJoueur(Player player, boolean force) {
        if (roleManager.estExclu(player)) {
            player.sendMessage(Messages.get("menuclasse.les_admins_afk_ne_choisissent_pas"));
            return;
        }

        if (classeManager.getClasse(player).isPresent()) {
            player.sendMessage(Messages.get("menuclasse.tu_as_deja_choisi_une_classe"));
            return;
        }

        choixAutorise = true;
        joueursDansLeMenu.add(player.getUniqueId());
        if (force) {
            menuForce.add(player.getUniqueId());
        }
        ouvrirMenu(player);
    }

    private void ouvrirMenu(Player player) {
        if (!player.isOnline()) {
            return;
        }

        Inventory menu = Bukkit.createInventory(null, 9, TITRE_GUI);

        for (int i = 0; i < SLOTS.length; i++) {
            menu.setItem(EMPLACEMENTS[i], classeManager.creerItemActif(SLOTS[i]));
        }

        player.openInventory(menu);
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

        int rawSlot = event.getRawSlot();

        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        Classe classe = classeDuSlot(rawSlot);

        if (classe == null) {
            return;
        }

        if (event.isLeftClick()) {
            UUID uuid = player.getUniqueId();
            enPreviewLivre.add(uuid);
            player.openBook(classeManager.creerLivre(classe));

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                enPreviewLivre.remove(uuid);

                if (!player.isOnline()) {
                    return;
                }
                if (!choixAutorise || !joueursDansLeMenu.contains(uuid)) {
                    return;
                }
                if (classeManager.getClasse(player).isPresent()) {
                    return;
                }
                ouvrirMenu(player);
            }, DELAI_REOUVERTURE_APRES_LIVRE);

            return;
        }

        if (!event.isRightClick()) {
            return;
        }

        if (classeManager.getClasse(player).isPresent()) {
            player.sendMessage(Messages.get("menuclasse.tu_as_deja_choisi_une_classe"));
            return;
        }

        joueursDansLeMenu.remove(player.getUniqueId());
        enPreviewLivre.remove(player.getUniqueId());
        menuForce.remove(player.getUniqueId());
        classeManager.assignerClasse(player, classe);

        Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
    }

    private Classe classeDuSlot(int rawSlot) {
        for (int i = 0; i < EMPLACEMENTS.length; i++) {
            if (EMPLACEMENTS[i] == rawSlot) {
                return SLOTS[i];
            }
        }
        return null;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (!TITRE_GUI.equals(event.getView().getTitle())) {
            return;
        }

        UUID uuid = player.getUniqueId();

        if (enPreviewLivre.contains(uuid)) {
            return;
        }

        if (!choixAutorise || !joueursDansLeMenu.contains(uuid)) {
            return;
        }

        if (!menuForce.contains(uuid)) {
            joueursDansLeMenu.remove(uuid);
            return;
        }

        Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> {
                    if (!player.isOnline()) {
                        return;
                    }

                    if (!choixAutorise || !joueursDansLeMenu.contains(uuid)) {
                        return;
                    }

                    ouvrirMenu(player);
                },
                1L
        );
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        joueursDansLeMenu.remove(event.getPlayer().getUniqueId());
        enPreviewLivre.remove(event.getPlayer().getUniqueId());
        menuForce.remove(event.getPlayer().getUniqueId());
    }

    public void arreterMenu() {
        choixAutorise = false;
        joueursDansLeMenu.clear();
        enPreviewLivre.clear();
        menuForce.clear();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (TITRE_GUI.equals(player.getOpenInventory().getTitle())) {
                player.closeInventory();
            }
        }
    }

    public static boolean aMenuClasseOuvert(Player player) {
        return TITRE_GUI.equals(player.getOpenInventory().getTitle());
    }
}
