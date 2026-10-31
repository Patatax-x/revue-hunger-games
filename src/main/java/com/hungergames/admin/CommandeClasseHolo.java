package com.hungergames.admin;

import com.hungergames.classe.Classe;
import com.hungergames.config.Messages;
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
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class CommandeClasseHolo implements CommandExecutor, Listener, TabCompleter {
    private static final Map<Classe, Material> OEUFS = new EnumMap<>(Classe.class);

    private static final Map<Classe, String> COULEUR_MM = new EnumMap<>(Classe.class);

    static {
        OEUFS.put(Classe.BOUCLIER, Material.TURTLE_SPAWN_EGG);
        OEUFS.put(Classe.VITESSE, Material.RABBIT_SPAWN_EGG);
        OEUFS.put(Classe.INVISIBLE, Material.ENDERMAN_SPAWN_EGG);
        OEUFS.put(Classe.FEU, Material.BLAZE_SPAWN_EGG);
        OEUFS.put(Classe.LOUP_GAROU, Material.WOLF_SPAWN_EGG);

        COULEUR_MM.put(Classe.BOUCLIER, "green");
        COULEUR_MM.put(Classe.VITESSE, "aqua");
        COULEUR_MM.put(Classe.INVISIBLE, "dark_purple");
        COULEUR_MM.put(Classe.FEU, "red");
        COULEUR_MM.put(Classe.LOUP_GAROU, "gold");
    }

    private static final int MODEL_DATA_BASE = 9500;

    private static final String ECHELLE_TEXTE = "1.0";

    private final JavaPlugin plugin;
    private final AtomicInteger compteurHologramme = new AtomicInteger(0);

    public CommandeClasseHolo(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command cmd, @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Messages.get("commun.cette_commande_doit_etre_executee_par"));
            return true;
        }

        for (Classe classe : Classe.values()) {
            player.getInventory().addItem(creerOeuf(classe));
        }

        player.sendMessage(Messages.get("commandeclasseholo.un_uf_par_classe_ajoute_ton"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }

    private ItemStack creerOeuf(Classe classe) {
        ItemStack item = new ItemStack(OEUFS.getOrDefault(classe, Material.PIG_SPAWN_EGG));
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(Messages.get("commandeclasseholo.hologramme", "classe", classe.getDisplayName()));
            meta.setLore(List.of(
                    Messages.get("commandeclasseholo.clic_droit_pose_le_titre"),
                    Messages.get("commandeclasseholo.la_description_de_cette_classe_ici")
            ));
            meta.setCustomModelData(MODEL_DATA_BASE + classe.ordinal() + 1);
            item.setItemMeta(meta);
        }

        return item;
    }

    private Classe trouverClasse(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) return null;

        for (Classe classe : Classe.values()) {
            if (item.getType() == OEUFS.get(classe) && meta.getCustomModelData() == MODEL_DATA_BASE + classe.ordinal() + 1) {
                return classe;
            }
        }
        return null;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        Classe classe = trouverClasse(player.getInventory().getItemInMainHand());
        if (classe == null) return;

        event.setCancelled(true);

        Location ancre = (event.getClickedBlock() != null)
                ? event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5)
                : player.getLocation();

        poserHologrammes(player, classe, ancre);
    }

    private void poserHologrammes(Player player, Classe classe, Location ancre) {
        int id = compteurHologramme.incrementAndGet();

        String nomTitre = "hg_classe_titre_" + id;
        String nomDesc = "hg_classe_desc_" + id;

        String x = fmt(ancre.getX());
        String z = fmt(ancre.getZ());

        String yTitre = fmt(ancre.getY() + 1.6);
        String yDesc = fmt(ancre.getY() + 0.1);

        String couleur = COULEUR_MM.getOrDefault(classe, "white");
        String titre = "<bold><" + couleur + ">" + ChatColor.stripColor(classe.getDisplayName()) + "</" + couleur + "></bold>";

        String ligneActifLabel = Messages.get("commandeclasseholo.yellow_actif_yellow");
        String ligneActifValeur = "<white>" + classe.getResumeActif() + "</white>";
        String lignePassifLabel = Messages.get("commandeclasseholo.yellow_passif_yellow");
        String lignePassifValeur = "<white>" + classe.getResumePassif() + "</white>";

        PermissionAttachment attachment = player.addAttachment(plugin);
        attachment.setPermission("FancyHolograms.admin", true);
        attachment.setPermission("fancyholograms.admin", true);

        try {
            executer(player, "hologram create text " + nomTitre);
            executer(player, "hologram edit " + nomTitre + " moveTo " + x + " " + yTitre + " " + z);
            executer(player, "hologram edit " + nomTitre + " setLine 1 " + titre);
            executer(player, "hologram edit " + nomTitre + " scale " + ECHELLE_TEXTE);

            executer(player, "hologram create text " + nomDesc);
            executer(player, "hologram edit " + nomDesc + " moveTo " + x + " " + yDesc + " " + z);
            executer(player, "hologram edit " + nomDesc + " setLine 1 " + ligneActifLabel);
            executer(player, "hologram edit " + nomDesc + " addLine " + ligneActifValeur);
            executer(player, "hologram edit " + nomDesc + " addLine " + lignePassifLabel);
            executer(player, "hologram edit " + nomDesc + " addLine " + lignePassifValeur);
            executer(player, "hologram edit " + nomDesc + " scale " + ECHELLE_TEXTE);
        } finally {
            player.removeAttachment(attachment);
        }

        player.sendMessage(Messages.get("commandeclasseholo.hologrammes_poses_ici", "classe", classe.getDisplayName()));
    }

    private String fmt(double valeur) {
        return String.format(Locale.ROOT, "%.2f", valeur);
    }

    private void executer(Player player, String commande) {
        try {
            Bukkit.dispatchCommand(player, commande);
        } catch (Exception exception) {
            plugin.getLogger().log(Level.SEVERE, "Échec de /" + commande, exception);
        }
    }
}
