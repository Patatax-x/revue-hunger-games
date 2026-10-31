package com.hungergames.admin;

import com.hungergames.config.Messages;
import com.hungergames.map.GameMap;
import com.hungergames.map.MapResetManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CommandeResetMap implements CommandExecutor, Listener, TabCompleter {
    private static final String TITRE_MENU = Messages.get("commanderesetmap.reinitialisation_des_maps");
    private static final String TITRE_CONFIRMATION = Messages.get("commanderesetmap.confirmer_la_reinitialisation");

    private static final int SLOT_AUTO = 22;
    private static final int RANGEE_MAPS = 9;
    private static final int MAPS_MAX_MENU = 9;

    private static final int SLOT_CONFIRMER = 11;
    private static final int SLOT_ANNULER = 15;

    private final MapResetManager mapResetManager;

    private final Map<UUID, GameMap> enAttenteConfirmation = new HashMap<>();

    public CommandeResetMap(MapResetManager mapResetManager) {
        this.mapResetManager = mapResetManager;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String @NonNull [] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("auto")) {
            return gererAuto(sender, args);
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                afficherEtatConsole(sender);
                return true;
            }
            ouvrirMenu(player);
            return true;
        }

        GameMap map = GameMap.parCle(args[0]);
        if (map == null) {
            sender.sendMessage(Messages.get("commanderesetmap.map_inconnue", "args", args[0]));
            return true;
        }

        mapResetManager.reinitialiser(map, sender);
        return true;
    }

    private boolean gererAuto(CommandSender sender, String[] args) {
        if (args.length == 1) {
            sender.sendMessage(Messages.get("commanderesetmap.reinitialisation_auto_apres_partie", "mapresetmanager", (mapResetManager.isAutoApresPartie() ? Messages.get("commanderesetmap.activee") : Messages.get("commanderesetmap.desactivee"))));
            sender.sendMessage(Messages.get("commanderesetmap.usage_resetmap_auto_on_off"));
            return true;
        }

        boolean actif = args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("true");
        mapResetManager.setAutoApresPartie(actif);

        if (actif) {
            sender.sendMessage(Messages.get("commanderesetmap.reinitialisation_auto_activee_la_map_sera"));
            sender.sendMessage(Messages.get("commanderesetmap.verifie_abord_que_resetmap_map_fonctionne"));
            sender.sendMessage(Messages.get("commanderesetmap.le_reglage_repart_off_au_prochain"));
        } else {
            sender.sendMessage(Messages.get("commanderesetmap.reinitialisation_auto_desactivee"));
        }
        return true;
    }

    private void afficherEtatConsole(CommandSender sender) {
        sender.sendMessage(Messages.get("commanderesetmap.reinitialisation_des_maps_2"));
        for (GameMap map : GameMap.values()) {
            sender.sendMessage(Messages.get("commanderesetmap.valeur_resumeetat", "valeur", map.getAffichage(), "resumeetat", resumeEtat(map)));
        }
        sender.sendMessage(Messages.get("commanderesetmap.auto_apres_partie", "mapresetmanager", (mapResetManager.isAutoApresPartie() ? Messages.get("commanderesetmap.activee_2") : Messages.get("commanderesetmap.desactivee_2"))));
    }

    private String resumeEtat(GameMap map) {
        if (!mapResetManager.sauvegardeDisponible(map)) {
            return Messages.get("commanderesetmap.aucune_sauvegarde_trouvee");
        }
        return mapResetManager.estPropre(map)
                ? Messages.get("commanderesetmap.propre_reinitialisee")
                : Messages.get("commun.jouee_depuis_la_derniere_reinitialisation");
    }

    private List<GameMap> mapsDuMenu() {
        List<GameMap> maps = GameMap.values();
        return maps.subList(0, Math.min(MAPS_MAX_MENU, maps.size()));
    }

    private void ouvrirMenu(Player admin) {
        Inventory menu = Bukkit.createInventory(null, 27, TITRE_MENU);

        List<GameMap> maps = mapsDuMenu();
        int[] emplacements = Menus.emplacementsCentres(maps.size());

        for (int i = 0; i < emplacements.length; i++) {
            GameMap map = maps.get(i);
            boolean sauvegarde = mapResetManager.sauvegardeDisponible(map);
            boolean propre = mapResetManager.estPropre(map);

            Material material;
            List<String> lore = new ArrayList<>();

            if (!sauvegarde) {
                material = Material.BARRIER;
                lore.add(Messages.get("commanderesetmap.aucun_dossier_de_sauvegarde_trouve"));
                lore.add(Messages.get("commanderesetmap.attendu", "dossiersauvegarde", (map.getDossierSauvegarde() != null ? map.getDossierSauvegarde() : Messages.get("commanderesetmap.backup_folder_dans_config_yml"))));
                lore.add(Messages.get("commanderesetmap.la_racine_des_mondes_du_serveur"));
            } else if (propre) {
                material = Material.LIME_CONCRETE;
                lore.add(Messages.get("commanderesetmap.propre_reinitialisee_2"));
                lore.add(Messages.get("commanderesetmap.aucune_partie_jouee_depuis"));
                lore.add("");
                lore.add(Messages.get("commanderesetmap.clic_pour_reinitialiser_quand_meme"));
            } else {
                material = Material.RED_CONCRETE;
                lore.add(Messages.get("commanderesetmap.jouee_depuis_la_derniere_reinitialisation"));
                lore.add(Messages.get("commanderesetmap.coffres_pilles_terrain_abime"));
                lore.add("");
                lore.add(Messages.get("commanderesetmap.clic_pour_reinitialiser"));
            }

            menu.setItem(RANGEE_MAPS + emplacements[i], Menus.creerItem(material, map.getAffichage(), lore));
        }

        boolean auto = mapResetManager.isAutoApresPartie();
        menu.setItem(SLOT_AUTO, Menus.creerItem(
                auto ? Material.LIME_DYE : Material.GRAY_DYE,
                auto ? Messages.get("commanderesetmap.reinitialisation_auto_activee") : Messages.get("commanderesetmap.reinitialisation_auto_desactivee_2"),
                List.of(
                        Messages.get("commanderesetmap.reinitialise_la_map_automatiquement"),
                        Messages.get("commanderesetmap.apres_chaque_fin_de_partie"),
                        "",
                        Messages.get("commanderesetmap.teste_abord_une_reinitialisation"),
                        Messages.get("commanderesetmap.manuelle_avant_activer"),
                        "",
                        Messages.get("commanderesetmap.repart_off_au_redemarrage"),
                        Messages.get("commanderesetmap.clic_pour_basculer")
                )));

        admin.openInventory(menu);
    }

    private void ouvrirConfirmation(Player admin, GameMap map) {
        enAttenteConfirmation.put(admin.getUniqueId(), map);

        Inventory menu = Bukkit.createInventory(null, 27, TITRE_CONFIRMATION);

        menu.setItem(4, Menus.creerItem(Material.FILLED_MAP, map.getAffichage(), List.of(
                Messages.get("commanderesetmap.cette_map_va_etre_remplacee_par"),
                Messages.get("commanderesetmap.une_copie_de_sa_sauvegarde"),
                "",
                Messages.get("commanderesetmap.tout_ce_qui_ete_construit_ou"),
                Messages.get("commanderesetmap.casse_dessus_sera_perdu")
        )));

        menu.setItem(SLOT_CONFIRMER, Menus.creerItem(Material.LIME_CONCRETE, Messages.get("commanderesetmap.confirmer"), List.of(
                Messages.get("commanderesetmap.reinitialise_maintenant", "valeur", ChatColor.stripColor(map.getAffichage()))
        )));

        menu.setItem(SLOT_ANNULER, Menus.creerItem(Material.RED_CONCRETE, Messages.get("commun.annuler"), List.of(
                Messages.get("commanderesetmap.ne_touche_rien")
        )));

        admin.openInventory(menu);
    }

    @EventHandler
    public void onClic(InventoryClickEvent event) {
        String titre = event.getView().getTitle();
        boolean estMenu = TITRE_MENU.equals(titre);
        boolean estConfirmation = TITRE_CONFIRMATION.equals(titre);

        if (!estMenu && !estConfirmation) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player admin)) {
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        if (estMenu) {
            gererClicMenu(admin, event.getRawSlot());
            return;
        }

        gererClicConfirmation(admin, event.getRawSlot());
    }

    private void gererClicMenu(Player admin, int slot) {
        if (slot == SLOT_AUTO) {
            boolean nouveau = !mapResetManager.isAutoApresPartie();
            mapResetManager.setAutoApresPartie(nouveau);
            admin.sendMessage(nouveau
                    ? Messages.get("commanderesetmap.reinitialisation_auto_activee_repart_off_au")
                    : Messages.get("commanderesetmap.reinitialisation_auto_desactivee"));
            ouvrirMenu(admin);
            return;
        }

        List<GameMap> maps = mapsDuMenu();
        int[] emplacements = Menus.emplacementsCentres(maps.size());
        GameMap map = null;
        for (int i = 0; i < emplacements.length; i++) {
            if (RANGEE_MAPS + emplacements[i] == slot) {
                map = maps.get(i);
            }
        }
        if (map == null) {
            return;
        }

        if (!mapResetManager.sauvegardeDisponible(map)) {
            admin.sendMessage(Messages.get("commanderesetmap.aucune_sauvegarde_trouvee_pour_rien_restaurer", "valeur", map.getAffichage()));
            return;
        }

        ouvrirConfirmation(admin, map);
    }

    private void gererClicConfirmation(Player admin, int slot) {
        GameMap map = enAttenteConfirmation.get(admin.getUniqueId());

        if (slot == SLOT_ANNULER || map == null) {
            enAttenteConfirmation.remove(admin.getUniqueId());
            admin.closeInventory();
            return;
        }

        if (slot != SLOT_CONFIRMER) {
            return;
        }

        enAttenteConfirmation.remove(admin.getUniqueId());
        admin.closeInventory();
        mapResetManager.reinitialiser(map, admin);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> choix = new ArrayList<>();
            for (GameMap map : GameMap.values()) {
                choix.add(map.getCle());
            }
            choix.add("auto");
            return choix;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("auto")) {
            return List.of("on", "off");
        }
        return List.of();
    }
}
