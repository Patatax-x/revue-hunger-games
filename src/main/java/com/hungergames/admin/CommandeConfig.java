package com.hungergames.admin;

import com.hungergames.config.Messages;
import com.hungergames.loot.ChestManager;
import com.hungergames.loot.ConfigMap;
import com.hungergames.loot.Rarete;
import com.hungergames.map.GameMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
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
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CommandeConfig implements CommandExecutor, Listener, TabCompleter {
    private static final String TITRE_LISTE = Messages.get("commandeconfig.config_des_maps");
    private static final String TITRE_EDITION = Messages.get("commandeconfig.config_coffres");
    private static final String TITRE_ANVIL = Messages.get("commandeconfig.valeur");

    private enum Champ { RAYON, RAYON_CLUSTER, NOMBRE_TOTAL, POURCENTAGE_CENTRE, CENTRE, CENTRE_CLUSTER, DELAI_BORDURE, DUREE_MAX_PARTIE }

    private static final int[] SLOTS_RARETE = {19, 20, 21, 22, 23};

    private static final Rarete[] RARETES_CONFIGURABLES = {
            Rarete.COMMUN, Rarete.PEU_COMMUN, Rarete.RARE, Rarete.EPIQUE, Rarete.LEGENDAIRE
    };

    private final JavaPlugin plugin;
    private final ChestManager chestManager;

    private final Map<UUID, GameMap> mapEnCours = new HashMap<>();
    private final Map<UUID, Champ> champEnCours = new HashMap<>();
    private final Map<UUID, String> saisieEnCours = new HashMap<>();

    public CommandeConfig(JavaPlugin plugin, ChestManager chestManager) {
        this.plugin = plugin;
        this.chestManager = chestManager;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String @NonNull [] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("nettoyer")) {
            int retires = chestManager.nettoyerToutesLesMaps();
            sender.sendMessage(Messages.get("commandeconfig.coffre_retire_sur_toutes_les_maps", "retires", retires));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("commun.cette_commande_doit_etre_executee_par"));
            return true;
        }

        ouvrirListeMaps(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("nettoyer");
        }
        return Collections.emptyList();
    }

    private void ouvrirListeMaps(Player player) {
        List<GameMap> maps = GameMap.values();
        Inventory menu = Bukkit.createInventory(null, Menus.tailleMenu(maps.size()), TITRE_LISTE);
        int[] emplacements = Menus.emplacementsCentres(maps.size());

        for (int i = 0; i < emplacements.length; i++) {
            GameMap map = maps.get(i);
            ItemStack item = new ItemStack(Material.CHEST);
            ItemMeta meta = item.getItemMeta();

            if (meta != null) {
                meta.setDisplayName(map.getAffichage());
                meta.setLore(List.of(Messages.get("commandeconfig.clique_pour_configurer_les_coffres")));
                item.setItemMeta(meta);
            }

            menu.setItem(emplacements[i], item);
        }

        player.openInventory(menu);
    }

    private void ouvrirEdition(Player player, GameMap map) {
        mapEnCours.put(player.getUniqueId(), map);

        ConfigMap config = chestManager.getConfig(map);
        Inventory menu = Bukkit.createInventory(null, 27, TITRE_EDITION);

        menu.setItem(10, Menus.creerItem(Material.ENDER_EYE, Messages.get("commandeconfig.centre_eparpillement_largages"),
                List.of(Messages.get("commandeconfig.formatcentre", "formatcentre", formatCentre(config)), Messages.get("commandeconfig.centre_de_la_map_defini_cote"), "", Messages.get("commandeconfig.clic_pour_modifier_ou_ici"))));
        menu.setItem(11, Menus.creerItem(Material.SPYGLASS, Messages.get("commandeconfig.rayon_eparpillement"),
                List.of(Messages.get("commandeconfig.blocs", "rayoneparpillement", config.getRayonEparpillement()), "", Messages.get("commandeconfig.clic_pour_modifier"))));
        menu.setItem(12, Menus.creerItem(Material.CHEST, Messages.get("commandeconfig.nombre_total_de_coffres"),
                List.of(Messages.get("commandeconfig.nombrecoffrestotal", "nombrecoffrestotal", config.getNombreCoffresTotal()), Messages.get("commandeconfig.reparti_centre_eparpille_selon_le"), "", Messages.get("commandeconfig.clic_pour_modifier"))));
        menu.setItem(13, Menus.creerItem(Material.ENDER_CHEST, Messages.get("commandeconfig.au_tas_central"),
                List.of(Messages.get("commandeconfig.centre_eparpilles", "pourcentagecentre", config.getPourcentageCentre(), "nombrecoffrescentre", config.getNombreCoffresCentre(), "nombrecoffreseparpilles", config.getNombreCoffresEparpilles()), "", Messages.get("commandeconfig.clic_pour_modifier"))));
        menu.setItem(14, Menus.creerItem(Material.TARGET, Messages.get("commandeconfig.rayon_du_tas_central"),
                List.of(Messages.get("commandeconfig.blocs_2", "rayoncluster", config.getRayonCluster()), Messages.get("commandeconfig.zone_resserree_20_par_defaut"), "", Messages.get("commandeconfig.clic_pour_modifier"))));
        menu.setItem(15, Menus.creerItem(Material.LODESTONE, Messages.get("commandeconfig.point_du_tas_central"),
                List.of(Messages.get("commandeconfig.formatcentrecluster", "formatcentrecluster", formatCentreCluster(config)), Messages.get("commandeconfig.independant_du_centre_general"), Messages.get("commandeconfig.pas_forcement_le_centre_de_la"), "", Messages.get("commandeconfig.clic_pour_modifier_ou_ici"))));
        menu.setItem(16, Menus.creerItem(Material.CLOCK, Messages.get("commandeconfig.delai_avant_retrecissement"),
                List.of(Messages.get("commandeconfig.delaiavantretrecissementsecondes", "delaiavantretrecissementsecondes", config.getDelaiAvantRetrecissementSecondes()), Messages.get("commandeconfig.bordure_pleine_jusque_la"), "", Messages.get("commandeconfig.clic_pour_modifier"))));
        menu.setItem(17, Menus.creerItem(Material.NETHER_STAR, Messages.get("commandeconfig.duree_max_de_la_partie"),
                List.of(Messages.get("commandeconfig.cloture_auto", "dureemaxpartiesecondes", config.getDureeMaxPartieSecondes()),
                        Messages.get("commandeconfig.retrecissement", "dureeretrecissementsecondes", config.getDureeRetrecissementSecondes()), "", Messages.get("commandeconfig.clic_pour_modifier"))));

        for (int i = 0; i < RARETES_CONFIGURABLES.length; i++) {
            menu.setItem(SLOTS_RARETE[i], creerItemRarete(RARETES_CONFIGURABLES[i], config));
        }

        menu.setItem(25, Menus.creerItem(Material.PAPER, Messages.get("commandeconfig.total_des_proportions"),
                List.of(Messages.get("commandeconfig.totalproportions", "totalproportions", config.getTotalProportions()), Messages.get("commandeconfig.pas_besoin_que_ca_fasse_100"), Messages.get("commandeconfig.ce_sont_des_poids_relatifs"))));
        menu.setItem(26, creerItemRetour());

        player.openInventory(menu);
    }

    private String formatCentre(ConfigMap config) {
        return String.format("%.0f %.0f %.0f", config.getCentreX(), config.getCentreY(), config.getCentreZ());
    }

    private String formatCentreCluster(ConfigMap config) {
        return String.format("%.0f %.0f %.0f", config.getCentreClusterX(), config.getCentreClusterY(), config.getCentreClusterZ());
    }

    private ItemStack creerItemRarete(Rarete rarete, ConfigMap config) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(rarete.getAffichage());
            meta.setLore(List.of(
                    Messages.get("commandeconfig.proportion", "proportion", config.getProportion(rarete)),
                    "",
                    Messages.get("commandeconfig.clic_gauche"),
                    Messages.get("commandeconfig.clic_droit")
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack creerItemRetour() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Messages.get("commandeconfig.retour"));
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String titre = event.getView().getTitle();

        if (TITRE_LISTE.equals(titre)) {
            gererClicListe(event);
            return;
        }

        if (TITRE_EDITION.equals(titre)) {
            gererClicEdition(event);
            return;
        }

        if (TITRE_ANVIL.equals(titre)) {
            event.setCancelled(true);
        }
    }

    private void gererClicListe(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int rawSlot = event.getRawSlot();
        List<GameMap> maps = GameMap.values();
        int[] emplacements = Menus.emplacementsCentres(maps.size());
        for (int i = 0; i < emplacements.length; i++) {
            if (emplacements[i] == rawSlot) {
                ouvrirEdition(player, maps.get(i));
                return;
            }
        }
    }

    private void gererClicEdition(InventoryClickEvent event) {
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        GameMap map = mapEnCours.get(player.getUniqueId());
        if (map == null) {
            return;
        }

        ConfigMap config = chestManager.getConfig(map);
        int rawSlot = event.getRawSlot();

        if (rawSlot == 10) {
            ouvrirAnvil(player, Champ.CENTRE, formatCentre(config));
            return;
        }
        if (rawSlot == 11) {
            ouvrirAnvil(player, Champ.RAYON, String.valueOf(config.getRayonEparpillement()));
            return;
        }
        if (rawSlot == 12) {
            ouvrirAnvil(player, Champ.NOMBRE_TOTAL, String.valueOf(config.getNombreCoffresTotal()));
            return;
        }
        if (rawSlot == 13) {
            ouvrirAnvil(player, Champ.POURCENTAGE_CENTRE, String.valueOf(config.getPourcentageCentre()));
            return;
        }
        if (rawSlot == 14) {
            ouvrirAnvil(player, Champ.RAYON_CLUSTER, String.valueOf(config.getRayonCluster()));
            return;
        }
        if (rawSlot == 15) {
            ouvrirAnvil(player, Champ.CENTRE_CLUSTER, formatCentreCluster(config));
            return;
        }
        if (rawSlot == 16) {
            ouvrirAnvil(player, Champ.DELAI_BORDURE, String.valueOf(config.getDelaiAvantRetrecissementSecondes()));
            return;
        }
        if (rawSlot == 17) {
            ouvrirAnvil(player, Champ.DUREE_MAX_PARTIE, String.valueOf(config.getDureeMaxPartieSecondes()));
            return;
        }
        if (rawSlot == 26) {
            ouvrirListeMaps(player);
            return;
        }

        for (int i = 0; i < SLOTS_RARETE.length; i++) {
            if (SLOTS_RARETE[i] == rawSlot) {
                int delta = event.isLeftClick() ? 5 : -5;

                Rarete rarete = RARETES_CONFIGURABLES[i];
                config.setProportion(rarete, config.getProportion(rarete) + delta);
                ouvrirEdition(player, map);
                return;
            }
        }
    }

    private void ouvrirAnvil(Player player, Champ champ, String valeurActuelle) {
        champEnCours.put(player.getUniqueId(), champ);

        Inventory anvil = Bukkit.createInventory(null, InventoryType.ANVIL, TITRE_ANVIL);
        ItemStack papier = new ItemStack(Material.PAPER);
        ItemMeta meta = papier.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(valeurActuelle);
            papier.setItemMeta(meta);
        }
        anvil.setItem(0, papier);
        player.openInventory(anvil);
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!TITRE_ANVIL.equals(event.getView().getTitle())) {
            return;
        }

        AnvilInventory anvil = event.getInventory();
        anvil.setRepairCost(0);

        if (!(event.getView().getPlayer() instanceof Player joueur)) {
            return;
        }

        ItemStack resultat = event.getResult();
        if (resultat != null && resultat.hasItemMeta() && resultat.getItemMeta().hasDisplayName()) {
            String saisie = ChatColor.stripColor(resultat.getItemMeta().getDisplayName());
            saisieEnCours.put(joueur.getUniqueId(), saisie);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (!TITRE_ANVIL.equals(event.getView().getTitle())) {
            return;
        }

        UUID uuid = player.getUniqueId();
        String saisie = saisieEnCours.remove(uuid);
        Champ champ = champEnCours.remove(uuid);
        GameMap map = mapEnCours.get(uuid);

        if (map == null) {
            return;
        }

        if (champ != null && saisie != null && !saisie.isBlank()) {
            appliquerSaisie(player, chestManager.getConfig(map), champ, saisie);
        }

        Bukkit.getScheduler().runTask(plugin, () -> ouvrirEdition(player, map));
    }

    private void appliquerSaisie(Player player, ConfigMap config, Champ champ, String saisie) {
        try {
            switch (champ) {
                case RAYON -> config.setRayonEparpillement(Integer.parseInt(saisie.trim()));
                case RAYON_CLUSTER -> config.setRayonCluster(Integer.parseInt(saisie.trim()));
                case NOMBRE_TOTAL -> config.setNombreCoffresTotal(Integer.parseInt(saisie.trim()));
                case POURCENTAGE_CENTRE -> config.setPourcentageCentre(Integer.parseInt(saisie.trim()));
                case DELAI_BORDURE -> config.setDelaiAvantRetrecissementSecondes(Integer.parseInt(saisie.trim()));
                case DUREE_MAX_PARTIE -> config.setDureeMaxPartieSecondes(Integer.parseInt(saisie.trim()));
                case CENTRE -> appliquerPosition(player, saisie, config::setCentre);
                case CENTRE_CLUSTER -> appliquerPosition(player, saisie, config::setCentreCluster);
            }
            player.sendMessage(Messages.get("commandeconfig.valeur_mise_jour"));
        } catch (NumberFormatException exception) {
            player.sendMessage(Messages.get("commandeconfig.valeur_invalide_inchangee"));
        }
    }

    private void appliquerPosition(Player player, String saisie, PositionSetter setter) {
        String texte = saisie.trim();

        if (texte.equalsIgnoreCase("ici")) {
            Location loc = player.getLocation();
            setter.set(loc.getX(), loc.getY(), loc.getZ());
            return;
        }

        String[] parties = texte.split("[ ,]+");

        if (parties.length != 3) {
            player.sendMessage(Messages.get("commandeconfig.format_attendu_ex_51_ou_ici"));
            return;
        }

        double x = Double.parseDouble(parties[0]);
        double y = Double.parseDouble(parties[1]);
        double z = Double.parseDouble(parties[2]);

        setter.set(x, y, z);
    }

    @FunctionalInterface
    private interface PositionSetter {
        void set(double x, double y, double z);
    }
}
