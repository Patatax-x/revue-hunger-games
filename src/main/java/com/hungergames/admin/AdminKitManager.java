package com.hungergames.admin;

import com.hungergames.config.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class AdminKitManager implements Listener {
    private enum Outil {
        MODE(0, Material.CLOCK, 9301, Messages.get("adminkitmanager.mode"), "/mode", List.of(Messages.get("adminkitmanager.ouvre_le_menu_off_lobby_fermeture"))),
        GO(1, Material.NETHER_STAR, 9302, Messages.get("adminkitmanager.go"), "/go", List.of(Messages.get("adminkitmanager.choisit_la_map_et_demarre"), Messages.get("adminkitmanager.vraiment_la_partie"))),
        CONFIG(2, Material.COMPARATOR, 9303, Messages.get("adminkitmanager.config"), "/config", List.of(Messages.get("adminkitmanager.regle_les_coffres_position"), Messages.get("adminkitmanager.rayon_quantites_raretes_par_map"))),

        HUNGER(3, Material.FILLED_MAP, 9304, Messages.get("adminkitmanager.vue_ensemble"), "/hunger", List.of(Messages.get("adminkitmanager.affiche_etat_complet_du_jeu"), Messages.get("adminkitmanager.joueurs_equipes_largages_bordure"), Messages.get("adminkitmanager.disponible_tout_moment_quel"), Messages.get("adminkitmanager.que_soit_le_mode"))),
        PNJ(4, Material.VILLAGER_SPAWN_EGG, 9305, Messages.get("adminkitmanager.mannequin"), "/pnj", List.of(Messages.get("adminkitmanager.cree_un_mannequin_entrainement"))),
        VERIF(6, Material.KNOWLEDGE_BOOK, 9307, Messages.get("adminkitmanager.verif"), "/verif", List.of(Messages.get("adminkitmanager.relance_le_menu_equipe_puis"), Messages.get("adminkitmanager.classe_pour_tout_le_monde"))),
        VANISH(7, Material.PHANTOM_MEMBRANE, 9308, Messages.get("adminkitmanager.vanish"), "/vanish", List.of(Messages.get("adminkitmanager.bascule_ta_visibilite_pour"), Messages.get("adminkitmanager.observer_sans_interferer"))),

        LOOT(8, Material.BLAZE_ROD, 9309, Messages.get("adminkitmanager.loot"), "/loot", List.of(Messages.get("adminkitmanager.ouvre_le_menu_rarete_variante"), Messages.get("adminkitmanager.modifie_agencement_un_pool_existant"), Messages.get("adminkitmanager.ou_clique_ensuite_sur_un_coffre"), Messages.get("adminkitmanager.pour_remplacer_tout_son_contenu")));

        final int slot;
        final Material material;
        final int modelData;
        final String nom;
        final String commande;
        final List<String> guide;

        Outil(int slot, Material material, int modelData, String nom, String commande, List<String> guide) {
            this.slot = slot;
            this.material = material;
            this.modelData = modelData;
            this.nom = nom;
            this.commande = commande;
            this.guide = guide;
        }
    }

    public void donnerKit(Player player) {
        for (Outil outil : Outil.values()) {
            player.getInventory().setItem(outil.slot, creerItem(outil));
        }
    }

    public void retirerOutilsInutilesEnJeu(Player player) {
        for (Outil outil : List.of(Outil.PNJ, Outil.VERIF, Outil.LOOT)) {
            ItemStack item = player.getInventory().getItem(outil.slot);
            if (item != null && correspond(item, outil)) {
                player.getInventory().setItem(outil.slot, null);
            }
        }
    }

    public void retirerKit(Player player) {
        for (Outil outil : Outil.values()) {
            ItemStack item = player.getInventory().getItem(outil.slot);
            if (item != null && correspond(item, outil)) {
                player.getInventory().setItem(outil.slot, null);
            }
        }
    }

    private ItemStack creerItem(Outil outil) {
        ItemStack item = new ItemStack(outil.material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(Messages.get("adminkitmanager.outil_outil2", "outil", outil.nom, "outil2", outil.commande));

            List<String> lore = new ArrayList<>(outil.guide);
            lore.add("");
            lore.add(Messages.get("adminkitmanager.clic_droit_pour_lancer", "outil", outil.commande));
            meta.setLore(lore);

            meta.setCustomModelData(outil.modelData);
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }

        return item;
    }

    private boolean correspond(ItemStack item, Outil outil) {
        if (item.getType() != outil.material) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == outil.modelData;
    }

    private Outil trouverOutil(ItemStack item) {
        if (item == null) return null;
        for (Outil outil : Outil.values()) {
            if (correspond(item, outil)) return outil;
        }
        return null;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        Outil outil = trouverOutil(player.getInventory().getItemInMainHand());

        if (outil == null) return;

        event.setCancelled(true);
        Bukkit.dispatchCommand(player, outil.commande.substring(1));
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (trouverOutil(event.getItemDrop().getItemStack()) != null) {
            event.setCancelled(true);
        }
    }
}
