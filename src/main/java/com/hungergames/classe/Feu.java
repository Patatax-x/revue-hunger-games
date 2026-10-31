package com.hungergames.classe;

import com.hungergames.config.Messages;
import com.hungergames.config.Reglages;
import com.hungergames.game.FreezeManager;
import com.hungergames.hud.ActionBar;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Feu implements Listener {
    private final JavaPlugin plugin;
    private final ClasseManager classeManager;
    private final FreezeManager freezeManager;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Long> actifJusqua = new HashMap<>();
    private final Map<UUID, BukkitTask> tachesClignotement = new HashMap<>();

    public Feu(JavaPlugin plugin, ClasseManager classeManager, FreezeManager freezeManager) {
        this.plugin = plugin;
        this.classeManager = classeManager;
        this.freezeManager = freezeManager;
        StartFeuTask();
        StartCooldownTask();
    }

    private void StartFeuTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (classeManager.possedeClasse(player, Classe.FEU)) {
                        player.addPotionEffect(
                                new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 60, 0, false
                                ));
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
                    if (!classeManager.possedeClasse(player, Classe.FEU)) {
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
                        player.sendActionBar(Messages.get("feu.boule_de_feu_lancee"));
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
        return Messages.get("feu.recharge", "temps", String.format("%02d:%02d", minutes, sec));
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
        if (!classeManager.possedeClasse(player, Classe.FEU)) return false;
        if (!classeManager.estItemActif(player.getInventory().getItemInMainHand(), Classe.FEU)) return false;

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

        TirerBouleDeFeu(player);

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

    private void TirerBouleDeFeu(Player player) {
        Fireball fireball = player.launchProjectile(
                Fireball.class,
                player.getEyeLocation().getDirection()
        );

        fireball.setShooter(player);
        fireball.setIsIncendiary(false);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.5f, 1.0f);
        player.getWorld().spawnParticle(Particle.FLAME, player.getEyeLocation(), 20, 0.2, 0.2, 0.2, 0.02);
    }

    @EventHandler
    public void onExplosionBouleDeFeu(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof Fireball fireball)) return;
        if (!(fireball.getShooter() instanceof Player)) return;

        event.blockList().clear();
    }

    @EventHandler
    public void onDegatsBouleDeFeu(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Fireball fireball)) return;
        if (!(fireball.getShooter() instanceof Player)) return;

        event.setDamage(event.getDamage() + 4.0);
    }

    public void nettoyer(Player player) {
        player.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
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
