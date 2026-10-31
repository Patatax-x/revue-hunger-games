package com.hungergames.admin;

import com.hungergames.config.Messages;
import com.hungergames.game.MatchScheduler;
import com.hungergames.loot.ChestManager;
import com.hungergames.loot.ConfigMap;
import com.hungergames.map.GameMap;
import com.hungergames.map.MapManager;
import com.hungergames.role.RoleManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;

public class MatchAdminKit implements Listener {
    private static final String TITRE_GUI = Messages.get("matchadminkit.largage_manuel");
    private static final double HAUTEUR_OBSERVATION = 10.0;

    private static final int SLOT_LARGAGE = 0;
    private static final int SLOT_ARRETER = 1;
    private static final int SLOT_PVP = 2;
    private static final int MODEL_DATA_LARGAGE = 9601;
    private static final int MODEL_DATA_ARRETER = 9602;
    private static final int MODEL_DATA_PVP = 9603;

    private final MapManager mapManager;
    private final ChestManager chestManager;
    private final MatchScheduler matchScheduler;
    private final RoleManager roleManager;
    private final AdminKitManager adminKitManager;
    private final CommandeVanish commandeVanish;

    public MatchAdminKit(MapManager mapManager, ChestManager chestManager, MatchScheduler matchScheduler, RoleManager roleManager, AdminKitManager adminKitManager, CommandeVanish commandeVanish) {
        this.mapManager = mapManager;
        this.chestManager = chestManager;
        this.matchScheduler = matchScheduler;
        this.roleManager = roleManager;
        this.adminKitManager = adminKitManager;
        this.commandeVanish = commandeVanish;
    }

    public void donnerKit(World monde, GameMap map) {
        Location observation = new Location(monde, map.getCentreX() + 0.5, map.getCentreY() + HAUTEUR_OBSERVATION, map.getCentreZ() + 0.5);

        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (!roleManager.estAdmin(admin)) {
                continue;
            }
            donnerKitPourJoueur(admin, observation);
        }
    }

    public void donnerKitPourJoueurPromuEnJeu(Player admin) {
        GameMap map = mapManager.getMapActuelle();
        World monde = Bukkit.getWorld(map.getCle());
        if (monde == null) {
            return;
        }

        Location observation = new Location(monde, map.getCentreX() + 0.5, map.getCentreY() + HAUTEUR_OBSERVATION, map.getCentreZ() + 0.5);
        donnerKitPourJoueur(admin, observation);
    }

    private void donnerKitPourJoueur(Player admin, Location observation) {
        admin.teleport(observation);
        admin.setFallDistance(0f);

        admin.setGameMode(GameMode.SURVIVAL);
        admin.setAllowFlight(true);
        admin.setFlying(true);

        commandeVanish.activer(admin);

        admin.getInventory().setItem(SLOT_LARGAGE, creerItem(Material.FIREWORK_ROCKET, MODEL_DATA_LARGAGE,
                Messages.get("matchadminkit.largage_manuel_2"), List.of(Messages.get("matchadminkit.choisis_un_joueur_ou_aleatoire"), Messages.get("matchadminkit.pour_faire_apparaitre_un_largage"), "", Messages.get("matchadminkit.clic_droit_pour_ouvrir_le_choix"))));
        admin.getInventory().setItem(SLOT_ARRETER, creerItem(Material.BARRIER, MODEL_DATA_ARRETER,
                Messages.get("matchadminkit.arreter_la_partie"), List.of(Messages.get("matchadminkit.cloture_la_partie_en_cours"), Messages.get("matchadminkit.identique_end"), "", Messages.get("matchadminkit.clic_droit_pour_arreter"))));
        admin.getInventory().setItem(SLOT_PVP, creerItem(Material.IRON_SWORD, MODEL_DATA_PVP,
                Messages.get("matchadminkit.pvp"), List.of(Messages.get("matchadminkit.active_desactive_le_combat"), Messages.get("matchadminkit.entre_joueurs_sur_cette_map"), "", Messages.get("matchadminkit.clic_droit_pour_basculer"))));

        adminKitManager.retirerOutilsInutilesEnJeu(admin);
    }

    public void retirerKit() {
        for (Player admin : Bukkit.getOnlinePlayers()) {
            if (!roleManager.estAdmin(admin)) {
                continue;
            }

            admin.setGameMode(GameMode.CREATIVE);
            adminKitManager.donnerKit(admin);
            commandeVanish.desactiver(admin);
        }
    }

    private ItemStack creerItem(Material material, int modelData, String nom, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(nom);
            meta.setLore(lore);
            meta.setCustomModelData(modelData);
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }

        return item;
    }

    private boolean correspond(ItemStack item, int modelData) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == modelData;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack enMain = player.getInventory().getItemInMainHand();

        if (correspond(enMain, MODEL_DATA_LARGAGE)) {
            event.setCancelled(true);
            ouvrirChoixLargage(player);
        } else if (correspond(enMain, MODEL_DATA_ARRETER)) {
            event.setCancelled(true);
            Bukkit.dispatchCommand(player, "end");
        } else if (correspond(enMain, MODEL_DATA_PVP)) {
            event.setCancelled(true);
            basculerPvp(player);
        }
    }

    private void basculerPvp(Player admin) {
        GameMap map = mapManager.getMapActuelle();
        World monde = Bukkit.getWorld(map.getCle());
        if (monde == null) {
            admin.sendMessage(Messages.get("commun.erreur_map_introuvable"));
            return;
        }

        boolean nouveauEtat = !monde.getPVP();
        monde.setPVP(nouveauEtat);

        Bukkit.broadcastMessage(nouveauEtat
                ? Messages.get("commun.pvp_reactive_par_un_admin")
                : Messages.get("commun.pvp_desactive_par_un_admin"));
    }

    public void ouvrirChoixLargage(Player admin) {
        Inventory gui = Bukkit.createInventory(null, 27, TITRE_GUI);

        ItemStack aleatoire = new ItemStack(Material.ENDER_EYE);
        ItemMeta metaAleatoire = aleatoire.getItemMeta();
        if (metaAleatoire != null) {
            metaAleatoire.setDisplayName(Messages.get("matchadminkit.aleatoire"));
            metaAleatoire.setLore(List.of(Messages.get("matchadminkit.systeme_normal_joueur"), Messages.get("matchadminkit.tire_au_sort_automatiquement")));
            aleatoire.setItemMeta(metaAleatoire);
        }
        gui.setItem(0, aleatoire);

        int slot = 9;
        for (Player cible : matchScheduler.getJoueursEligibles()) {
            if (slot >= gui.getSize()) break;

            ItemStack tete = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta itemMeta = tete.getItemMeta();
            if (itemMeta instanceof SkullMeta meta) {
                meta.setOwningPlayer(cible);
                meta.setDisplayName(Messages.get("commun.cible", "cible", cible.getName()));
                meta.setLore(List.of(Messages.get("matchadminkit.fait_apparaitre_le_largage"), Messages.get("matchadminkit.pres_de_ce_joueur")));
                tete.setItemMeta(meta);
            }
            gui.setItem(slot, tete);
            slot++;
        }

        admin.openInventory(gui);
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!TITRE_GUI.equals(event.getView().getTitle())) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player admin)) {
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null) {
            return;
        }

        Player cible = null;
        if (item.getType() == Material.PLAYER_HEAD && item.getItemMeta() instanceof SkullMeta meta && meta.getOwningPlayer() != null) {
            cible = Bukkit.getPlayer(meta.getOwningPlayer().getUniqueId());
        } else if (item.getType() != Material.ENDER_EYE) {
            return;
        }

        GameMap map = mapManager.getMapActuelle();
        World monde = Bukkit.getWorld(map.getCle());
        admin.closeInventory();

        if (monde == null) {
            admin.sendMessage(Messages.get("commun.erreur_map_introuvable"));
            return;
        }

        ConfigMap config = chestManager.getConfig(map);
        boolean succes = matchScheduler.declencherLargageManuel(monde, map, config, cible);
        if (!succes) {
            admin.sendMessage(Messages.get("matchadminkit.aucune_position_libre_trouvee_pour_le"));
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (correspond(item, MODEL_DATA_LARGAGE) || correspond(item, MODEL_DATA_ARRETER) || correspond(item, MODEL_DATA_PVP)) {
            event.setCancelled(true);
        }
    }
}
