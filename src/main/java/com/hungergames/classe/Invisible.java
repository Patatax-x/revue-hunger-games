package com.hungergames.classe;

import com.hungergames.config.Messages;
import com.hungergames.config.Reglages;
import com.hungergames.equipe.EquipeManager;
import com.hungergames.game.FreezeManager;
import com.hungergames.hud.ActionBar;
import com.hungergames.role.RoleManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Invisible implements Listener {
    private static final String TITRE_GUI = Messages.get("invisible.choisir_une_prime");

    private static final long DUREE_PRIME = Reglages.entier("classes.bounty-seconds") * 1000L;

    private static final long DUREE_COOLDOWN = Reglages.entier("classes.cooldown-seconds") * 1000L;

    private final JavaPlugin plugin;
    private final ClasseManager classeManager;
    private final EquipeManager equipeManager;
    private final FreezeManager freezeManager;
    private final RoleManager roleManager;
    private final Map<UUID, Team> equipeSauvegardee = new HashMap<>();

    private final Set<UUID> joueursInvisibles = new HashSet<>();
    private final Map<UUID, Location> dernieresPositions = new HashMap<>();

    private final Map<UUID, UUID> primesParPrimeur = new HashMap<>();

    private final Map<UUID, UUID> primeurParCible = new HashMap<>();

    private final Map<UUID, Long> expirationParCible = new HashMap<>();

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Long> actifJusqua = new HashMap<>();
    private final Map<UUID, BukkitTask> tachesClignotement = new HashMap<>();

    private final Map<UUID, Integer> coeursPrimeGagnes = new HashMap<>();

    public Invisible(JavaPlugin plugin, ClasseManager classeManager, EquipeManager equipeManager, FreezeManager freezeManager, RoleManager roleManager) {
        this.plugin = plugin;
        this.classeManager = classeManager;
        this.equipeManager = equipeManager;
        this.freezeManager = freezeManager;
        this.roleManager = roleManager;

        startInvisibleTask();
        startPrimeTask();
        startCooldownTask();
    }

    private void startInvisibleTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();

                    if (!classeManager.possedeClasse(player, Classe.INVISIBLE)) {
                        retirerInvisibilite(player);
                        dernieresPositions.remove(uuid);
                        continue;
                    }

                    Location actuelle = player.getLocation();
                    Location ancienne = dernieresPositions.get(uuid);

                    if (ancienne == null) {
                        dernieresPositions.put(uuid, actuelle.clone());
                        continue;
                    }

                    boolean aBouge = aChangeDePosition(ancienne, actuelle);
                    dernieresPositions.put(uuid, actuelle.clone());

                    if (aBouge) {
                        retirerInvisibilite(player);
                    } else {
                        rendreInvisible(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private boolean aChangeDePosition(Location ancienne, Location actuelle) {
        if (ancienne.getWorld() == null || actuelle.getWorld() == null) {
            return true;
        }

        if (!ancienne.getWorld().equals(actuelle.getWorld())) {
            return true;
        }

        return ancienne.getX() != actuelle.getX()
                || ancienne.getY() != actuelle.getY()
                || ancienne.getZ() != actuelle.getZ();
    }

    private void rendreInvisible(Player player) {
        UUID uuid = player.getUniqueId();

        if (joueursInvisibles.contains(uuid)) {
            return;
        }

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.INVISIBILITY,
                Integer.MAX_VALUE,
                0,
                false,
                false,
                false
        ));

        joueursInvisibles.add(uuid);
    }

    private void retirerInvisibilite(Player player) {
        UUID uuid = player.getUniqueId();

        if (!joueursInvisibles.remove(uuid)) {
            return;
        }

        player.removePotionEffect(PotionEffectType.INVISIBILITY);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (!classeManager.possedeClasse(player, Classe.INVISIBLE)) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        if (aChangeDePosition(event.getFrom(), event.getTo())) {
            retirerInvisibilite(player);
        }
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (classeManager.estItemActif(event.getPlayer().getInventory().getItemInMainHand(), Classe.INVISIBLE)) {
            // setCancelled ne bloque pas l'usage de l'item : la longue-vue zoomerait quand même.
            event.setUseItemInHand(Event.Result.DENY);
        }

        if (declencher(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClicEntite(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (declencher(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private boolean declencher(Player player) {
        if (!classeManager.possedeClasse(player, Classe.INVISIBLE)) return false;
        if (!classeManager.estItemActif(player.getInventory().getItemInMainHand(), Classe.INVISIBLE)) return false;

        if (freezeManager.estGele(player)) {
            ActionBar.prioritaire(player, Messages.get("commun.en_attente_du_debut_de_la"));
            return true;
        }

        UUID uuid = player.getUniqueId();

        if (primesParPrimeur.containsKey(uuid)) {
            player.sendMessage(Messages.get("invisible.tu_as_deja_une_prime_active"));
            return true;
        }

        Long derniereUtilisation = cooldowns.get(uuid);

        if (derniereUtilisation != null) {
            long tempsRestant = derniereUtilisation + DUREE_COOLDOWN - System.currentTimeMillis();

            if (tempsRestant > 0) {
                player.sendMessage(Messages.get("invisible.cooldown_restant", "formattemps", formatTemps(tempsRestant)));
                clignoterCooldown(player);
                return true;
            }

            cooldowns.remove(uuid);
        }

        ouvrirGuiPrime(player);

        return true;
    }

    private void clignoterCooldown(Player player) {
        UUID uuid = player.getUniqueId();

        BukkitTask ancienne = tachesClignotement.remove(uuid);
        if (ancienne != null) {
            ancienne.cancel();
        }

        BukkitTask tache = new BukkitRunnable() {
            private final String[] couleurs = {Messages.get("commun.texte_2"), Messages.get("commun.texte_3"), Messages.get("commun.texte_4")};
            private int tick = 0;

            @Override
            public void run() {
                if (tick >= 10 || !player.isOnline()) {
                    tachesClignotement.remove(uuid);
                    cancel();
                    return;
                }
                ActionBar.prioritaire(player, Messages.get("commun.en_recharge", "couleurs", couleurs[tick % couleurs.length]), 600L);
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 4L);

        tachesClignotement.put(uuid, tache);
    }

    private void ouvrirGuiPrime(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, TITRE_GUI);

        int slot = 0;

        for (Player cible : Bukkit.getOnlinePlayers()) {
            if (cible.equals(player)) {
                continue;
            }

            if (slot >= gui.getSize()) {
                break;
            }

            ItemStack tete = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta itemMeta = tete.getItemMeta();

            if (!(itemMeta instanceof SkullMeta meta)) {
                continue;
            }

            meta.setOwningPlayer(cible);
            meta.setDisplayName(Messages.get("invisible.cible", "cible", cible.getName()));
            meta.setLore(List.of(
                    Messages.get("invisible.clique_pour_mettre_une_prime"),
                    Messages.get("invisible.duree_minutes"),
                    Messages.get("invisible.tue_durant_la_prime_le_tueur"),
                    Messages.get("invisible.survit_la_prime_la_cible_gagne")
            ));

            tete.setItemMeta(meta);
            gui.setItem(slot, tete);

            slot++;
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!TITRE_GUI.equals(event.getView().getTitle())) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player primeur)) {
            return;
        }

        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }

        ItemStack item = event.getCurrentItem();

        if (item == null || item.getType() != Material.PLAYER_HEAD) {
            return;
        }

        ItemMeta itemMeta = item.getItemMeta();

        if (!(itemMeta instanceof SkullMeta meta)) {
            return;
        }

        if (meta.getOwningPlayer() == null) {
            return;
        }

        UUID uuidCible = meta.getOwningPlayer().getUniqueId();
        Player cible = Bukkit.getPlayer(uuidCible);

        if (cible == null) {
            primeur.sendMessage(Messages.get("invisible.ce_joueur_est_plus_connecte"));
            primeur.closeInventory();
            return;
        }

        mettrePrime(primeur, cible);

        Bukkit.getScheduler().runTask(plugin, () -> primeur.closeInventory());
    }

    private void mettrePrime(Player primeur, Player cible) {
        UUID uuidPrimeur = primeur.getUniqueId();
        UUID uuidCible = cible.getUniqueId();

        if (primeur.equals(cible)) {
            primeur.sendMessage(Messages.get("invisible.tu_ne_peux_pas_te_mettre"));
            return;
        }

        if (primesParPrimeur.containsKey(uuidPrimeur)) {
            primeur.sendMessage(Messages.get("invisible.tu_as_deja_une_prime_active"));
            return;
        }

        UUID primeurExistant = primeurParCible.get(uuidCible);
        if (primeurExistant != null) {
            Player autrePrimeur = Bukkit.getPlayer(primeurExistant);
            String nomAutre = autrePrimeur != null ? autrePrimeur.getName() : Messages.get("invisible.un_autre_joueur");
            primeur.sendMessage(Messages.get("invisible.deja_une_prime_posee_par_impossible", "cible", cible.getName(), "nomautre", nomAutre));
            Bukkit.broadcastMessage(Messages.get("invisible.tente_de_poser_une_prime_sur", "primeur", primeur.getName(), "cible", cible.getName()));
            return;
        }

        primesParPrimeur.put(uuidPrimeur, uuidCible);
        primeurParCible.put(uuidCible, uuidPrimeur);
        expirationParCible.put(uuidCible, System.currentTimeMillis() + DUREE_PRIME);
        cooldowns.put(uuidPrimeur, System.currentTimeMillis());
        actifJusqua.put(uuidPrimeur, System.currentTimeMillis() + 2000L);

        marquerCible(cible);
        effetVisuel(cible);

        primeur.sendMessage(Messages.get("invisible.prime_placee_sur", "cible", cible.getName()));
        cible.sendMessage(Messages.get("invisible.une_prime_ete_placee_sur_toi"));
    }

    private String cleEquipePrime(Player cible) {
        return "au_prime_" + cible.getUniqueId().toString().substring(0, 8);
    }

    private void marquerCible(Player cible) {
        Team equipeActuelle = equipeManager.getTeamCosmetique(cible);
        if (equipeActuelle != null) {
            equipeSauvegardee.put(cible.getUniqueId(), equipeActuelle);
        }

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String cle = cleEquipePrime(cible);
        Team equipePrime = scoreboard.getTeam(cle);
        if (equipePrime == null) {
            equipePrime = scoreboard.registerNewTeam(cle);
        }
        equipePrime.setColor(ChatColor.RED);
        equipePrime.setPrefix(equipeActuelle != null ? equipeActuelle.getPrefix() : "");
        equipePrime.addEntry(cible.getName());

        cible.addPotionEffect(new PotionEffect(
                PotionEffectType.GLOWING,
                (int) (DUREE_PRIME / 50L),
                0,
                false,
                false,
                true
        ));
    }

    private void retirerMarquage(Player cible) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team equipePrime = scoreboard.getTeam(cleEquipePrime(cible));
        if (equipePrime != null) {
            equipePrime.unregister();
        }
        cible.removePotionEffect(PotionEffectType.GLOWING);

        Team sauvegardee = equipeSauvegardee.remove(cible.getUniqueId());
        if (sauvegardee != null) {
            equipeManager.restaurerTeamCosmetique(cible, sauvegardee);
        }
    }

    private void effetVisuel(Player cible) {
        cible.getWorld().playSound(cible.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.5f, 1.0f);
        cible.getWorld().spawnParticle(Particle.WITCH, cible.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.05);
    }

    private void donnerCoeur(Player player) {
        double nouveauMax = player.getMaxHealth() + 2.0;
        player.setMaxHealth(nouveauMax);
        player.setHealth(nouveauMax);
        coeursPrimeGagnes.merge(player.getUniqueId(), 1, Integer::sum);
    }

    @EventHandler
    public void onMortCible(PlayerDeathEvent event) {
        Player cible = event.getEntity();
        UUID uuidCible = cible.getUniqueId();

        if (!expirationParCible.containsKey(uuidCible)) {
            return;
        }

        terminerPrime(uuidCible);

        Player tueur = cible.getKiller();

        if (tueur != null) {
            donnerCoeur(tueur);
            tueur.sendMessage(Messages.get("invisible.tu_as_tue_la_cible_de"));
        }
    }

    private void startPrimeTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long maintenant = System.currentTimeMillis();

                for (UUID uuidCible : new HashSet<>(expirationParCible.keySet())) {
                    Long expiration = expirationParCible.get(uuidCible);

                    if (expiration == null || maintenant >= expiration) {
                        terminerPrime(uuidCible);

                        Player cible = Bukkit.getPlayer(uuidCible);

                        if (cible != null) {
                            donnerCoeur(cible);
                            cible.sendMessage(Messages.get("invisible.tu_as_survecu_la_prime_tu"));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void terminerPrime(UUID uuidCible) {
        UUID uuidPrimeur = primeurParCible.remove(uuidCible);
        expirationParCible.remove(uuidCible);

        if (uuidPrimeur != null) {
            primesParPrimeur.remove(uuidPrimeur);

            Player primeur = Bukkit.getPlayer(uuidPrimeur);
            if (primeur != null) {
                primeur.sendMessage(Messages.get("invisible.la_prime_sur_ta_cible_est"));
            }
        }

        Player cible = Bukkit.getPlayer(uuidCible);
        if (cible != null) {
            retirerMarquage(cible);
        }
    }

    private void startCooldownTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long maintenant = System.currentTimeMillis();

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!classeManager.possedeClasse(player, Classe.INVISIBLE)) {
                        continue;
                    }

                    if (player.getGameMode() == GameMode.SPECTATOR) {
                        continue;
                    }

                    if (!ActionBar.hudAutorise(player)) {
                        continue;
                    }

                    UUID uuid = player.getUniqueId();

                    Long finActif = actifJusqua.get(uuid);
                    if (finActif != null && maintenant < finActif) {
                        player.sendActionBar(Messages.get("invisible.prime_posee"));
                        continue;
                    }

                    Long derniereUtilisation = cooldowns.get(uuid);

                    if (derniereUtilisation == null) {
                        player.sendActionBar(Messages.get("commun.capacite_prete"));
                        continue;
                    }

                    long tempsRestant = derniereUtilisation + DUREE_COOLDOWN - maintenant;

                    if (tempsRestant <= 0) {
                        cooldowns.remove(uuid);
                        continue;
                    }

                    player.sendActionBar(Messages.get("invisible.cooldown_prime", "formattemps", formatTemps(tempsRestant)));
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private String formatTemps(long millis) {
        long secondes = millis / 1000;
        long minutes = secondes / 60;
        long secondesRestantes = secondes % 60;
        return String.format("%02d:%02d", minutes, secondesRestantes);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        nettoyer(event.getPlayer());
    }

    public void nettoyer(Player player) {
        UUID uuid = player.getUniqueId();

        retirerInvisibilite(player);
        dernieresPositions.remove(uuid);
        actifJusqua.remove(uuid);
        cooldowns.remove(uuid);

        BukkitTask tache = tachesClignotement.remove(uuid);
        if (tache != null) {
            tache.cancel();
        }

        UUID uuidCible = primesParPrimeur.remove(uuid);
        if (uuidCible != null) {
            primeurParCible.remove(uuidCible);
            expirationParCible.remove(uuidCible);

            Player cible = Bukkit.getPlayer(uuidCible);
            if (cible != null) {
                retirerMarquage(cible);
            }
        }

        if (expirationParCible.containsKey(uuid)) {
            terminerPrime(uuid);
        }
    }

    public void reinitialiserCooldown(Player player) {
        cooldowns.remove(player.getUniqueId());
    }

    public void reinitialiserPourNouvellePartie() {
        for (Map.Entry<UUID, Integer> entree : coeursPrimeGagnes.entrySet()) {
            Player joueur = Bukkit.getPlayer(entree.getKey());
            if (joueur != null) {
                double retrait = entree.getValue() * 2.0;
                double nouveauMax = Math.max(20.0, joueur.getMaxHealth() - retrait);
                joueur.setMaxHealth(nouveauMax);
                if (joueur.getHealth() > nouveauMax) {
                    joueur.setHealth(nouveauMax);
                }
            }
        }
        coeursPrimeGagnes.clear();

        for (UUID uuidCible : new HashSet<>(expirationParCible.keySet())) {
            terminerPrime(uuidCible);
        }

        for (UUID uuid : new HashSet<>(joueursInvisibles)) {
            Player joueur = Bukkit.getPlayer(uuid);
            if (joueur != null) {
                retirerInvisibilite(joueur);
            }
        }
        dernieresPositions.clear();

        cooldowns.clear();
        actifJusqua.clear();
        for (BukkitTask tache : tachesClignotement.values()) {
            tache.cancel();
        }
        tachesClignotement.clear();
    }
}
