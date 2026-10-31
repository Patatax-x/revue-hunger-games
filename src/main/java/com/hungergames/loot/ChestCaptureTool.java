package com.hungergames.loot;

import com.hungergames.config.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ChestCaptureTool implements Listener {
    private static final int MODEL_DATA = 9501;
    private static final String TITRE_GUI_CIBLE = Messages.get("chestcapturetool.loot_choisir_la_cible");
    private static final String TITRE_GUI_EDITEUR = Messages.get("chestcapturetool.loot_editer_le_pool");

    private static final int[] SLOTS_RARETE = {10, 11, 12, 13, 14, 15};

    private final JavaPlugin plugin;
    private final NamespacedKey marqueur;
    private final LootRegistry lootRegistry;

    private final Map<UUID, Rarete> rareteActive = new HashMap<>();
    private final Map<UUID, Integer> varianteActive = new HashMap<>();

    private record SessionEdition(Rarete rarete, int variante) {}

    private final Map<UUID, SessionEdition> editionEnCours = new HashMap<>();

    public ChestCaptureTool(JavaPlugin plugin, LootRegistry lootRegistry) {
        this.plugin = plugin;
        this.marqueur = new NamespacedKey(plugin, "loot_capture_tool");
        this.lootRegistry = lootRegistry;
    }

    public ItemStack creerItem() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(Messages.get("chestcapturetool.selectionneur_de_coffres"));
            meta.setLore(List.of(
                    Messages.get("chestcapturetool.clic_droit_dans_le_vide_pour"),
                    Messages.get("chestcapturetool.la_rarete_variante_capturer"),
                    Messages.get("chestcapturetool.puis_clic_droit_sur_un_coffre"),
                    Messages.get("chestcapturetool.remplacer_son_contenu_par_le_sien")
            ));
            meta.getPersistentDataContainer().set(marqueur, PersistentDataType.BYTE, (byte) 1);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.setCustomModelData(MODEL_DATA);
            item.setItemMeta(meta);
        }

        return item;
    }

    private boolean estOutil(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(marqueur, PersistentDataType.BYTE);
    }

    public void ouvrirChoixCible(Player joueur) {
        Inventory gui = Bukkit.createInventory(null, 27, TITRE_GUI_CIBLE);

        Rarete[] raretes = Rarete.values();
        for (int i = 0; i < raretes.length; i++) {
            gui.setItem(SLOTS_RARETE[i], creerItemRarete(joueur, raretes[i]));
        }

        joueur.sendMessage(Messages.get("chestcapturetool.clic_gauche_variante_clic_droit_variante"));
        joueur.sendMessage(Messages.get("chestcapturetool.pour_le_largage_variantes_shift_gauche"));
        joueur.openInventory(gui);
    }

    private ItemStack creerItemRarete(Player joueur, Rarete rarete) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            boolean estActive = rarete.equals(rareteActive.get(joueur.getUniqueId()));
            Integer varianteEnCours = varianteActive.get(joueur.getUniqueId());

            meta.setDisplayName(rarete.getAffichage() + (estActive ? Messages.get("chestcapturetool.variante", "varianteencours", varianteEnCours) : ""));
            meta.setLore(List.of(
                    Messages.get("chestcapturetool.variante_item_clic_gauche", "lootregistry", lootRegistry.taille(rarete, 1)),
                    Messages.get("chestcapturetool.variante_item_clic_droit", "lootregistry", lootRegistry.taille(rarete, 2))
            ));
            if (estActive) {
                meta.setEnchantmentGlintOverride(true);
            }
            item.setItemMeta(meta);
        }

        return item;
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!TITRE_GUI_CIBLE.equals(event.getView().getTitle())) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player joueur)) {
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        Rarete rarete = null;
        for (int i = 0; i < SLOTS_RARETE.length; i++) {
            if (SLOTS_RARETE[i] == event.getRawSlot()) {
                rarete = Rarete.values()[i];
                break;
            }
        }
        if (rarete == null) {
            return;
        }

        int varianteBase = event.isRightClick() ? 2 : 1;
        boolean secondePaire = event.isShiftClick() && rarete.getNombreVariantes() >= varianteBase + 2;
        final int variante = secondePaire ? varianteBase + 2 : varianteBase;

        rareteActive.put(joueur.getUniqueId(), rarete);
        varianteActive.put(joueur.getUniqueId(), variante);

        joueur.sendMessage(Messages.get("chestcapturetool.cible_variante_clique_sur_un_coffre", "rarete", rarete.getAffichage(), "variante", variante));
        joueur.sendMessage(Messages.get("chestcapturetool.ou_reorganise_retire_des_objets_ci"));

        Rarete raretePourEdition = rarete;
        Bukkit.getScheduler().runTask(plugin, () -> ouvrirEditeurPool(joueur, raretePourEdition, variante));
    }

    private static final int TAILLE_EDITEUR = 54;

    private void ouvrirEditeurPool(Player joueur, Rarete rarete, int variante) {
        List<ItemStack> pool = lootRegistry.getPool(rarete, variante);

        Inventory gui = Bukkit.createInventory(null, TAILLE_EDITEUR, TITRE_GUI_EDITEUR);

        for (int i = 0; i < pool.size() && i < TAILLE_EDITEUR; i++) {
            gui.setItem(i, pool.get(i).clone());
        }

        if (pool.isEmpty()) {
            joueur.sendMessage(Messages.get("chestcapturetool.ce_pool_est_vide_pour_instant"));
        }

        editionEnCours.put(joueur.getUniqueId(), new SessionEdition(rarete, variante));
        joueur.openInventory(gui);
    }

    @EventHandler
    public void onEditeurClick(InventoryClickEvent event) {
        if (!TITRE_GUI_EDITEUR.equals(event.getView().getTitle())) {
            return;
        }

        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            return;
        }

        // Réorganiser reste libre, mais rien ne doit entrer depuis l'inventaire du joueur.
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getBottomInventory())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEditeurDrag(InventoryDragEvent event) {
        if (TITRE_GUI_EDITEUR.equals(event.getView().getTitle())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEditeurClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player joueur)) {
            return;
        }
        if (!TITRE_GUI_EDITEUR.equals(event.getView().getTitle())) {
            return;
        }

        SessionEdition session = editionEnCours.remove(joueur.getUniqueId());
        if (session == null) {
            return;
        }

        List<ItemStack> contenu = new ArrayList<>();
        for (ItemStack item : event.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                contenu.add(item);
            }
        }

        lootRegistry.remplacer(session.rarete(), session.variante(), contenu);
        joueur.sendMessage(Messages.get("chestcapturetool.agencement_de_variante_enregistre_item", "session", session.rarete().getAffichage(), "session2", session.variante(), "contenu", contenu.size()));
    }

    @EventHandler
    public void onClicCoffre(PlayerInteractEvent event) {
        if (!estOutil(event.getItem())) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player joueur = event.getPlayer();

        boolean estCoffre = action == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && event.getClickedBlock().getState() instanceof Chest;

        if (!estCoffre) {
            event.setCancelled(true);
            ouvrirChoixCible(joueur);
            return;
        }

        event.setCancelled(true);
        if (!(event.getClickedBlock().getState() instanceof Container conteneur)) {
            return;
        }
        capturerCoffre(joueur, conteneur);
    }

    private void capturerCoffre(Player joueur, Container chest) {
        UUID uuid = joueur.getUniqueId();
        Rarete rarete = rareteActive.get(uuid);
        Integer variante = varianteActive.get(uuid);

        if (rarete == null || variante == null) {
            joueur.sendMessage(Messages.get("chestcapturetool.choisis_abord_une_rarete_variante_ouverture"));
            ouvrirChoixCible(joueur);
            return;
        }

        List<ItemStack> contenu = new ArrayList<>();
        for (ItemStack item : chest.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                contenu.add(item);
            }
        }

        if (contenu.isEmpty()) {
            joueur.sendMessage(Messages.get("chestcapturetool.ce_coffre_est_vide_rien_capturer"));
            return;
        }

        lootRegistry.remplacer(rarete, variante, contenu);
        joueur.sendMessage(Messages.get("chestcapturetool.item_capture_pour_variante_remplace_ancien", "contenu", contenu.size(), "rarete", rarete.getAffichage(), "variante", variante));
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (estOutil(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }
}
