package com.hungergames.classe;

import com.hungergames.config.Messages;
import com.hungergames.config.Reglages;
import com.hungergames.equipe.EquipeManager;
import com.hungergames.game.FreezeManager;
import com.hungergames.game.KillTracker;
import com.hungergames.hud.ActionBar;
import com.hungergames.role.RoleManager;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LoupGarou implements Listener {
    private static final double BONUS_VIE = 10.0;

    private static final double RAYON_DETECTION_LOUPS = 30.0;
    private static final long INTERVALLE_CIBLAGE = 20L;

    private static final int NOMBRE_CHIENS = 3;

    // Tag persistant : un chien qui survit à un redémarrage doit rester reconnu, la mémoire du plugin ne suffit pas.
    private static final String TAG_CHIEN = "hg_loup_armee";

    private final JavaPlugin plugin;
    private final ClasseManager classeManager;
    private final FreezeManager freezeManager;
    private final KillTracker killTracker;
    private final EquipeManager equipeManager;
    private final RoleManager roleManager;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, List<Wolf>> armees = new HashMap<>();

    private final Set<UUID> aBonusVie = new HashSet<>();
    private final Map<UUID, Long> actifJusqua = new HashMap<>();
    private final Map<UUID, BukkitTask> tachesClignotement = new HashMap<>();

    public LoupGarou(JavaPlugin plugin, ClasseManager classeManager, FreezeManager freezeManager, KillTracker killTracker, EquipeManager equipeManager, RoleManager roleManager) {
        this.plugin = plugin;
        this.classeManager = classeManager;
        this.freezeManager = freezeManager;
        this.killTracker = killTracker;
        this.equipeManager = equipeManager;
        this.roleManager = roleManager;
        StartVieTask();
        StartCooldownTask();
        StartCiblageTask();
    }

    private boolean estChienDArmee(Entity entite) {
        if (!(entite instanceof Wolf wolf)) {
            return false;
        }
        if (wolf.getScoreboardTags().contains(TAG_CHIEN)) {
            return true;
        }
        for (List<Wolf> chiens : armees.values()) {
            if (chiens.contains(wolf)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onChienAttaqueJoueur(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victime)) {
            return;
        }
        if (!estChienDArmee(event.getDamager())) {
            return;
        }

        if (!killTracker.estPartieDemarree()) {
            event.setCancelled(true);
            return;
        }

        if (!(event.getDamager() instanceof Wolf chien) || chien.getOwner() == null) {
            return;
        }

        UUID proprietaire = chien.getOwner().getUniqueId();
        if (victime.getUniqueId().equals(proprietaire)) {
            event.setCancelled(true);
            return;
        }

        Player maitre = Bukkit.getPlayer(proprietaire);
        if (maitre == null) {
            return;
        }

        UUID partenaire = equipeManager.getPartenaire(maitre);
        if (partenaire != null && partenaire.equals(victime.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChienCibleJoueur(EntityTargetLivingEntityEvent event) {
        if (killTracker.estPartieDemarree()) {
            return;
        }
        if (!(event.getTarget() instanceof Player)) {
            return;
        }
        if (estChienDArmee(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    private void StartVieTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (classeManager.possedeClasse(player, Classe.LOUP_GAROU)) {
                        AjouterVie(player);
                    } else {
                        RetirerVie(player);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void AjouterVie(Player player) {
        UUID uuid = player.getUniqueId();

        if (aBonusVie.add(uuid)) {
            double vieMax = player.getMaxHealth() + BONUS_VIE;
            player.setMaxHealth(vieMax);

            if (player.getHealth() < vieMax) {
                player.setHealth(vieMax);
            }
        }
    }

    private void RetirerVie(Player player) {
        UUID uuid = player.getUniqueId();

        if (!aBonusVie.remove(uuid)) {
            return;
        }

        double vieMax = Math.max(20.0, player.getMaxHealth() - BONUS_VIE);
        player.setMaxHealth(vieMax);

        if (player.getHealth() > vieMax) {
            player.setHealth(vieMax);
        }
    }

    private void StartCooldownTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                long cooldown = Reglages.entier("classes.cooldown-seconds") * 1000L;

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!classeManager.possedeClasse(player, Classe.LOUP_GAROU)) {
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
                    if (finActif != null && now < finActif) {
                        player.sendActionBar(Messages.get("loupgarou.armee_invoquee"));
                        continue;
                    }

                    if (!cooldowns.containsKey(uuid)) {
                        player.sendActionBar(Messages.get("commun.capacite_prete"));
                        continue;
                    }

                    long lastUse = cooldowns.get(uuid);
                    long timeLeft = (lastUse + cooldown) - now;

                    if (timeLeft <= 0) {
                        cooldowns.remove(uuid);
                        continue;
                    }

                    String message = FormatMessageTemps(timeLeft);
                    player.sendActionBar(message);
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    private String FormatMessageTemps(long millis) {
        long secondes = millis / 1000;
        long minutes = secondes / 60;
        long sec = secondes % 60;

        return Messages.get("loupgarou.recharge", "temps", String.format("%02d:%02d", minutes, sec));
    }

    @EventHandler
    public void OnRightClick(PlayerInteractEvent event) {
        Action action = event.getAction();

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        if (declencher(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClicEntite(PlayerInteractEntityEvent event) {
        if (declencher(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private boolean declencher(Player player) {
        if (!classeManager.possedeClasse(player, Classe.LOUP_GAROU)) return false;
        if (!classeManager.estItemActif(player.getInventory().getItemInMainHand(), Classe.LOUP_GAROU)) return false;

        if (freezeManager.estGele(player)) {
            ActionBar.prioritaire(player, Messages.get("commun.en_attente_du_debut_de_la"));
            return true;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldown = Reglages.entier("classes.cooldown-seconds") * 1000L;

        if (cooldowns.containsKey(uuid)) {
            long lastUse = cooldowns.get(uuid);
            long timeLeft = (lastUse + cooldown) - now;
            if (timeLeft > 0) {
                clignoterCooldown(player);
                return true;
            }
        }
        cooldowns.put(uuid, now);
        actifJusqua.put(uuid, now + 2000L);

        MonArmee(player);

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

    private void MonArmee(Player player) {
        UUID uuid = player.getUniqueId();

        SupprimerArmee(player);

        List<Wolf> chiens = new ArrayList<>();

        for (int i = 0; i < NOMBRE_CHIENS; i++) {
            double angle = (Math.PI * 2 / NOMBRE_CHIENS) * i;
            double x = Math.cos(angle) * 2.0;
            double z = Math.sin(angle) * 2.0;

            Location location = player.getLocation()
                    .clone()
                    .add(x, 0, z);

            Wolf chien = (Wolf) player.getWorld().spawnEntity(
                    location,
                    EntityType.WOLF
            );

            chien.setTamed(true);
            chien.setOwner(player);
            chien.setSitting(false);
            chien.setCollarColor(DyeColor.RED);
            chien.setCustomName(Messages.get("loupgarou.chien_de", "joueur", player.getName()));
            chien.setCustomNameVisible(false);

            chien.addScoreboardTag(TAG_CHIEN);

            chiens.add(chien);
        }

        armees.put(uuid, chiens);
    }

    private boolean estPraticable(Location location) {
        return !location.getBlock().getType().isSolid()
                && !location.clone().add(0, 1, 0).getBlock().getType().isSolid();
    }

    private void SupprimerArmee(Player player) {
        UUID uuid = player.getUniqueId();
        List<Wolf> chiens = armees.remove(uuid);

        if (chiens == null) {
            return;
        }

        for (Wolf chien : chiens) {
            if (chien != null && chien.isValid()) {
                chien.remove();
            }
        }
    }

    @EventHandler
    public void onMortProprietaire(PlayerDeathEvent event) {
        SupprimerArmee(event.getEntity());
    }

    private void StartCiblageTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!killTracker.estPartieDemarree()) {
                    for (List<Wolf> chiens : armees.values()) {
                        for (Wolf chien : chiens) {
                            if (chien != null && chien.isValid()) {
                                // setTarget n'émet pas EntityTargetLivingEntityEvent : le garde-fou de l'événement ne s'applique pas à cet appel.
                                chien.setTarget(null);
                                chien.setAngry(false);
                            }
                        }
                    }
                    return;
                }

                for (Map.Entry<UUID, List<Wolf>> entry : armees.entrySet()) {
                    Player proprietaire = Bukkit.getPlayer(entry.getKey());
                    if (proprietaire == null) continue;

                    UUID partenaireUuid = equipeManager.getPartenaire(proprietaire);

                    for (Wolf chien : entry.getValue()) {
                        if (chien == null || !chien.isValid()) continue;

                        Player cible = trouverCibleLaPlusProche(chien, proprietaire, partenaireUuid);
                        chien.setTarget(cible);

                        chien.setAngry(cible != null);
                    }
                }
            }
        }.runTaskTimer(plugin, INTERVALLE_CIBLAGE, INTERVALLE_CIBLAGE);
    }

    private Player trouverCibleLaPlusProche(Wolf chien, Player proprietaire, UUID partenaireUuid) {
        Player plusProche = null;
        double meilleureDistance = RAYON_DETECTION_LOUPS;

        for (Player candidat : chien.getWorld().getPlayers()) {
            if (candidat.equals(proprietaire)) continue;
            if (candidat.getUniqueId().equals(partenaireUuid)) continue;
            if (candidat.getGameMode() != GameMode.SURVIVAL) continue;
            if (roleManager.estExclu(candidat)) continue;

            double distance = chien.getLocation().distance(candidat.getLocation());
            if (distance < meilleureDistance) {
                meilleureDistance = distance;
                plusProche = candidat;
            }
        }

        return plusProche;
    }

    public void nettoyer(Player player) {
        SupprimerArmee(player);
        RetirerVie(player);
        cooldowns.remove(player.getUniqueId());
        actifJusqua.remove(player.getUniqueId());

        BukkitTask tache = tachesClignotement.remove(player.getUniqueId());
        if (tache != null) {
            tache.cancel();
        }
    }

    public void reinitialiserCooldown(Player player) {
        cooldowns.remove(player.getUniqueId());
    }

    public void supprimerChiensOrphelins() {
        for (World monde : Bukkit.getWorlds()) {
            for (Wolf wolf : monde.getEntitiesByClass(Wolf.class)) {
                if (wolf.getScoreboardTags().contains(TAG_CHIEN)) {
                    wolf.remove();
                }
            }
        }
    }

    @EventHandler
    public void onProprietaireQuitte(PlayerQuitEvent event) {
        SupprimerArmee(event.getPlayer());
    }

    public void reinitialiserPourNouvellePartie() {
        for (UUID uuid : new HashSet<>(aBonusVie)) {
            Player joueur = Bukkit.getPlayer(uuid);
            if (joueur != null) {
                RetirerVie(joueur);
            }
        }
        aBonusVie.clear();

        for (List<Wolf> chiens : armees.values()) {
            for (Wolf chien : chiens) {
                if (chien != null && chien.isValid()) {
                    chien.remove();
                }
            }
        }
        armees.clear();
        supprimerChiensOrphelins();

        cooldowns.clear();
        actifJusqua.clear();
        for (BukkitTask tache : tachesClignotement.values()) {
            tache.cancel();
        }
        tachesClignotement.clear();
    }
}
