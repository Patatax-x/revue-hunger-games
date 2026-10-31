package com.hungergames.classe;

import com.hungergames.config.Messages;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public class ClasseManager implements Listener {
    private final JavaPlugin plugin;

    private final NamespacedKey cleProprietaire;

    private Defence defence;
    private Vitesse vitesse;
    private Invisible invisible;
    private Feu feu;
    private LoupGarou loupGarou;

    private final List<Consumer<Player>> ecouteursAssignation = new ArrayList<>();

    public ClasseManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.cleProprietaire = new NamespacedKey(plugin, "classe_proprietaire");
        demarrerAntiDrop();
    }

    public void ajouterEcouteurAssignation(Consumer<Player> ecouteur) {
        ecouteursAssignation.add(ecouteur);
    }

    public void enregistrer(Defence defence, Vitesse vitesse, Invisible invisible, Feu feu, LoupGarou loupGarou) {
        this.defence = defence;
        this.vitesse = vitesse;
        this.invisible = invisible;
        this.feu = feu;
        this.loupGarou = loupGarou;
    }

    // Les tags sont sauvegardés dans le fichier du joueur : ils survivent à sa déconnexion. LobbyManager.onPlayerJoin les purge en mode OFF.
    public Optional<Classe> getClasse(Player player) {
        for (Classe classe : Classe.values()) {
            if (player.getScoreboardTags().contains(classe.getTag())) {
                return Optional.of(classe);
            }
        }
        return Optional.empty();
    }

    public boolean possedeClasse(Player player, Classe classe) {
        return player.getScoreboardTags().contains(classe.getTag());
    }

    public void assignerClasse(Player player, Classe classe) {
        retirerClasse(player);

        player.addScoreboardTag(classe.getTag());

        player.getInventory().addItem(creerLivre(classe, player));
        player.getInventory().addItem(creerItemActif(classe, player));

        player.sendMessage(Messages.get("classemanager.tu_es_maintenant", "classe", classe.getDisplayName()));

        for (Consumer<Player> ecouteur : ecouteursAssignation) {
            ecouteur.accept(player);
        }
    }

    public void retirerClasse(Player player) {
        Optional<Classe> ancienne = getClasse(player);

        if (ancienne.isEmpty()) {
            return;
        }

        Classe classe = ancienne.get();

        player.removeScoreboardTag(classe.getTag());

        if (defence != null) defence.nettoyer(player);
        if (vitesse != null) vitesse.nettoyer(player);
        if (invisible != null) invisible.nettoyer(player);
        if (feu != null) feu.nettoyer(player);
        if (loupGarou != null) loupGarou.nettoyer(player);

        retirerItems(player, classe);
    }

    public void reinitialiserCooldownPourJoueur(Player player) {
        if (defence != null) defence.reinitialiserCooldown(player);
        if (vitesse != null) vitesse.reinitialiserCooldown(player);
        if (invisible != null) invisible.reinitialiserCooldown(player);
        if (feu != null) feu.reinitialiserCooldown(player);
        if (loupGarou != null) loupGarou.reinitialiserCooldown(player);
    }

    public void reinitialiserPourNouvellePartie() {
        if (defence != null) defence.reinitialiserPourNouvellePartie();
        if (vitesse != null) vitesse.reinitialiserPourNouvellePartie();
        if (invisible != null) invisible.reinitialiserPourNouvellePartie();
        if (feu != null) feu.reinitialiserPourNouvellePartie();
        if (loupGarou != null) loupGarou.reinitialiserPourNouvellePartie();
    }

    private void retirerItems(Player player, Classe classe) {
        ItemStack[] contenu = player.getInventory().getContents();

        for (int i = 0; i < contenu.length; i++) {
            ItemStack item = contenu[i];

            if (item == null) continue;

            boolean estLivre = item.getType() == Material.WRITTEN_BOOK && correspondModelData(item, classe);
            boolean estItemActif = estItemActif(item, classe);

            if (estLivre || estItemActif) {
                player.getInventory().setItem(i, null);
            }
        }
    }

    public ItemStack creerLivre(Classe classe) {
        ItemStack livre = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta rawMeta = livre.getItemMeta();

        if (!(rawMeta instanceof BookMeta meta)) {
            return livre;
        }

        meta.setTitle(classe.getDisplayName());
        meta.setAuthor(Messages.get("classemanager.hunger_games"));
        meta.addPage(classe.getPageLivre());
        meta.setCustomModelData(classe.getModelData());

        livre.setItemMeta(meta);

        return livre;
    }

    public ItemStack creerLivre(Classe classe, Player proprietaire) {
        ItemStack livre = creerLivre(classe);
        ItemMeta meta = livre.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(cleProprietaire, PersistentDataType.STRING, proprietaire.getUniqueId().toString());
            livre.setItemMeta(meta);
        }
        return livre;
    }

    public ItemStack creerItemActif(Classe classe) {
        ItemStack item = new ItemStack(classe.getItemActif());
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        meta.setDisplayName(classe.getDisplayName());
        meta.setLore(List.of(Messages.get("classemanager.clic_droit_pour_activer_le_pouvoir")));
        meta.setCustomModelData(classe.getModelData());
        meta.setEnchantmentGlintOverride(true);

        item.setItemMeta(meta);

        return item;
    }

    public ItemStack creerItemActif(Classe classe, Player proprietaire) {
        ItemStack item = creerItemActif(classe);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(cleProprietaire, PersistentDataType.STRING, proprietaire.getUniqueId().toString());
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean appartientA(ItemStack item, Player joueur) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        String proprietaire = meta.getPersistentDataContainer().get(cleProprietaire, PersistentDataType.STRING);
        return joueur.getUniqueId().toString().equals(proprietaire);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();

        for (Classe classe : Classe.values()) {
            boolean estLivre = item.getType() == Material.WRITTEN_BOOK && correspondModelData(item, classe);
            if (estLivre || estItemActif(item, classe)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    public boolean estItemActif(ItemStack item, Classe classe) {
        if (item == null || item.getType() != classe.getItemActif()) {
            return false;
        }

        return correspondModelData(item, classe);
    }

    private boolean correspondModelData(ItemStack item, Classe classe) {
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasCustomModelData() && meta.getCustomModelData() == classe.getModelData();
    }

    private boolean possedeItemActif(Player player, Classe classe) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && estItemActif(item, classe) && appartientA(item, player)) {
                return true;
            }
        }
        return false;
    }

    private boolean possedeLivre(Player player, Classe classe) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.WRITTEN_BOOK && correspondModelData(item, classe) && appartientA(item, player)) {
                return true;
            }
        }
        return false;
    }

    private void demarrerAntiDrop() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    getClasse(player).ifPresent(classe -> {
                        if (!possedeItemActif(player, classe)) {
                            player.getInventory().addItem(creerItemActif(classe, player));
                        }
                        if (!possedeLivre(player, classe)) {
                            player.getInventory().addItem(creerLivre(classe, player));
                        }
                    });
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory top = event.getView().getTopInventory();
        if (top.getType() == InventoryType.CRAFTING || top.getHolder() == null) {
            return;
        }

        ItemStack itemConcerne = null;

        if (event.getClickedInventory() != null && event.getClickedInventory().equals(top)) {
            itemConcerne = event.getCursor();
        } else if (event.isShiftClick() && event.getClickedInventory() != null && !event.getClickedInventory().equals(top)) {
            itemConcerne = event.getCurrentItem();
        }

        if (itemConcerne == null && event.getClickedInventory() != null && event.getClickedInventory().equals(top)) {
            if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
                itemConcerne = player.getInventory().getItem(event.getHotbarButton());
            } else if (event.getClick() == ClickType.SWAP_OFFHAND) {
                itemConcerne = player.getInventory().getItemInOffHand();
            }
        }

        if (estProtege(itemConcerne)) {
            event.setCancelled(true);
            player.sendMessage(Messages.get("classemanager.cet_objet_ne_peut_pas_etre"));
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getType() == InventoryType.CRAFTING || top.getHolder() == null) {
            return;
        }

        if (!estProtege(event.getOldCursor())) {
            return;
        }

        for (int slot : event.getRawSlots()) {
            if (slot < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private boolean estProtege(ItemStack item) {
        if (item == null) return false;
        for (Classe classe : Classe.values()) {
            boolean estLivre = item.getType() == Material.WRITTEN_BOOK && correspondModelData(item, classe);
            if (estLivre || estItemActif(item, classe)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMortRetirerItemsDeClasse(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::estProtege);
    }
}
