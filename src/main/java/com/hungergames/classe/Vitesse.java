package com.hungergames.classe;

import com.hungergames.config.Messages;
import com.hungergames.config.Reglages;
import com.hungergames.game.FreezeManager;
import com.hungergames.hud.ActionBar;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Vitesse implements Listener {
    private final JavaPlugin plugin;
    private final ClasseManager classeManager;
    private final FreezeManager freezeManager;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Long> actifJusqua = new HashMap<>();
    private final Map<UUID, BukkitTask> tachesClignotement = new HashMap<>();

    public Vitesse(JavaPlugin plugin, ClasseManager classeManager, FreezeManager freezeManager) {
        this.plugin = plugin;
        this.classeManager = classeManager;
        this.freezeManager = freezeManager;
        StartVitesseTask();
        StartCooldownTask();
    }

    private void StartVitesseTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()){
                    if (classeManager.possedeClasse(player, Classe.VITESSE)) {
                        player.addPotionEffect(
                                new PotionEffect(PotionEffectType.SPEED, 60, 0, false)
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
                    if (!classeManager.possedeClasse(player, Classe.VITESSE)) {
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
                        player.sendActionBar(Messages.get("vitesse.dash_active"));
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
        return Messages.get("vitesse.recharge", "temps", String.format("%02d:%02d", minutes, sec));
    }

    @EventHandler
    private void OnRightCLick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (!classeManager.possedeClasse(player, Classe.VITESSE)) return;
        if (!classeManager.estItemActif(player.getInventory().getItemInMainHand(), Classe.VITESSE)) return;

        RayTraceResult vise = player.rayTraceEntities(4);
        if (vise != null && vise.getHitEntity() != null) {
            return;
        }

        event.setCancelled(true);

        if (freezeManager.estGele(player)) {
            ActionBar.prioritaire(player, Messages.get("commun.en_attente_du_debut_de_la"));
            return;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldown = Reglages.entier("classes.cooldown-seconds") * 1000L;

        if (cooldowns.containsKey(uuid)) {
            long lastUse = cooldowns.get(uuid);
            long timeLeft = (lastUse + cooldown) - now;
            if (timeLeft > 0) {
                clignoterCooldown(player);
                return;
            }
        }
        cooldowns.put(uuid, now);
        actifJusqua.put(uuid, now + 2000L);

        double power = 5;

        Vector direction = player.getLocation().getDirection().normalize();
        Vector dash = direction.multiply(power);

        if (dash.getY() > 1) {
            dash.setY(1);
        }

        player.setVelocity(dash);
        EffetVisuel(player);
    }

    @EventHandler
    public void onClicEntite(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();

        if (!classeManager.estItemActif(player.getInventory().getItemInMainHand(), Classe.VITESSE)) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onDegatsChute(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;

        Long finActif = actifJusqua.get(player.getUniqueId());
        if (finActif != null && System.currentTimeMillis() < finActif) {
            event.setCancelled(true);
        }
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

    private void EffetVisuel(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.6f);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 15, 0.3, 0.2, 0.3, 0.02);

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ >= 8 || !player.isOnline()) {
                    cancel();
                    return;
                }
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 4, 0.2, 0.1, 0.2, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void nettoyer(Player player) {
        player.removePotionEffect(PotionEffectType.SPEED);
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
