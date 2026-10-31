package com.hungergames.classe;

import com.hungergames.config.Messages;
import com.hungergames.config.Reglages;
import com.hungergames.game.FreezeManager;
import com.hungergames.hud.ActionBar;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Defence implements Listener {
    private final JavaPlugin plugin;
    private final ClasseManager classeManager;
    private final FreezeManager freezeManager;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Long> actifJusqua = new HashMap<>();
    private final Map<UUID, BukkitTask> tachesClignotement = new HashMap<>();

    public Defence(JavaPlugin plugin, ClasseManager classeManager, FreezeManager freezeManager) {
        this.plugin = plugin;
        this.classeManager = classeManager;
        this.freezeManager = freezeManager;
        StartResistanceTask();
        StartCooldownTask();
    }

    private void StartResistanceTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (classeManager.possedeClasse(player, Classe.BOUCLIER)) {
                        player.addPotionEffect(
                                new PotionEffect(PotionEffectType.RESISTANCE, 60, 0, true, false)
                        );
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void StartCooldownTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                long cooldown = Reglages.entier("classes.cooldown-seconds") * 1000L;

                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!classeManager.possedeClasse(player, Classe.BOUCLIER)) {
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
                        player.sendActionBar(Messages.get("defence.protection_active"));
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
        return Messages.get("defence.recharge", "temps", String.format("%02d:%02d", minutes, sec));
    }

    @EventHandler
    private void OnRightClick(PlayerInteractEvent event) {
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
        if (!classeManager.possedeClasse(player, Classe.BOUCLIER)) return false;
        if (!classeManager.estItemActif(player.getInventory().getItemInMainHand(), Classe.BOUCLIER)) return false;

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

        ActiverProtectionMax(player);

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

    private void ActiverProtectionMax(Player player) {
        player.setInvulnerable(true);
        actifJusqua.put(player.getUniqueId(), System.currentTimeMillis() + 5000L);
        RepousserEnnemis(player);
        EffetVisuel(player);

        Bukkit.getScheduler().runTaskLater(
                plugin,
                () -> player.setInvulnerable(false),
                100L
        );
    }

    private void EffetVisuel(Player player) {
        World world = player.getWorld();
        Location centre = player.getLocation();

        world.playSound(centre, Sound.ITEM_SHIELD_BLOCK, 1.5f, 1.0f);

        for (double rayon = 0.5; rayon <= 5.0; rayon += 0.5) {
            for (int angle = 0; angle < 360; angle += 20) {
                double rad = Math.toRadians(angle);
                double x = centre.getX() + rayon * Math.cos(rad);
                double z = centre.getZ() + rayon * Math.sin(rad);

                world.spawnParticle(Particle.SWEEP_ATTACK, x, centre.getY() + 1, z, 0, 0, 0, 0, 0);
            }
        }
    }

    private void RepousserEnnemis(Player source) {
        double distanceMax = 7.0;
        double force = 10.0;

        for (Entity cible : source.getLocation().getNearbyEntities(distanceMax, distanceMax, distanceMax)) {
            if (!(cible instanceof LivingEntity)) continue;
            if (cible.equals(source)) continue;
            if (!source.hasLineOfSight(cible)) continue;

            double dist = source.getLocation().distance(cible.getLocation());
            if (dist >= distanceMax || dist == 0) continue;

            Vector direction = cible.getLocation().toVector()
                    .subtract(source.getLocation().toVector())
                    .normalize();

            Location nouvellePos = calculerDestinationSure(cible.getLocation(), direction, force);

            cible.teleport(nouvellePos);
            if (cible instanceof Player joueurCible) {
                joueurCible.sendMessage(Messages.get("defence.tu_as_ete_repousse_par_le", "source", source.getName()));
            }
        }
    }

    private Location calculerDestinationSure(Location origine, Vector direction, double distance) {
        Vector pas = direction.clone().normalize().multiply(0.5);
        int etapes = (int) Math.ceil(distance / 0.5);

        Location derniereSure = origine.clone();
        Location courante = origine.clone();

        for (int i = 0; i < etapes; i++) {
            courante = courante.clone().add(pas);
            if (!estPraticable(courante)) {
                break;
            }
            derniereSure = courante;
        }

        return derniereSure;
    }

    private boolean estPraticable(Location location) {
        return !location.getBlock().getType().isSolid()
                && !location.clone().add(0, 1, 0).getBlock().getType().isSolid();
    }

    public void nettoyer(Player player) {
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.setInvulnerable(false);
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

    public void reinitialiserPourNouvellePartie() {
        cooldowns.clear();
        actifJusqua.clear();
        for (BukkitTask tache : tachesClignotement.values()) {
            tache.cancel();
        }
        tachesClignotement.clear();
    }
}
