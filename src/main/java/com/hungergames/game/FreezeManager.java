package com.hungergames.game;

import com.hungergames.config.Messages;
import com.hungergames.config.Reglages;
import com.hungergames.hud.Titre;
import org.bukkit.Bukkit;
import org.bukkit.block.Container;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class FreezeManager implements Listener {
    private static final int DUREE_COMPTE_A_REBOURS = Reglages.entier("match.countdown-seconds");

    private final JavaPlugin plugin;
    private final Set<UUID> geles = new HashSet<>();
    private BossBar barreCompteARebours;

    public FreezeManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // Toute capacité active doit tester ceci : déplacer un joueur gelé par annulation de PlayerMoveEvent peut planter le serveur.
    public boolean estGele(Player player) {
        return geles.contains(player.getUniqueId());
    }

    public boolean compteAReboursEnCours() {
        return barreCompteARebours != null;
    }

    public void demarrerCompteARebours(List<Player> joueurs, Runnable auLancement) {
        for (Player joueur : joueurs) {
            geles.add(joueur.getUniqueId());

            joueur.addPotionEffect(new PotionEffect(
                    PotionEffectType.REGENERATION,
                    (DUREE_COMPTE_A_REBOURS + 1) * 20,
                    1,
                    false,
                    false,
                    true
            ));
        }

        barreCompteARebours = Bukkit.createBossBar(Messages.get("freezemanager.debut_dans", "duree_compte_a_rebours", DUREE_COMPTE_A_REBOURS), BarColor.RED, BarStyle.SOLID);
        for (Player joueur : joueurs) {
            barreCompteARebours.addPlayer(joueur);
        }

        tick(joueurs, DUREE_COMPTE_A_REBOURS, auLancement);
    }

    private void tick(List<Player> joueurs, int secondesRestantes, Runnable auLancement) {
        if (secondesRestantes <= 0) {
            liberer(joueurs);
            auLancement.run();
            return;
        }

        for (Player joueur : joueurs) {
            if (joueur.isOnline()) {
                Titre.immediat(joueur, Messages.get("freezemanager.secondesrestantes", "secondesrestantes", secondesRestantes), "", 0, 25, 5);
            }
        }

        if (barreCompteARebours != null) {
            barreCompteARebours.setTitle(Messages.get("freezemanager.debut_dans_2", "secondesrestantes", secondesRestantes));
            barreCompteARebours.setProgress(Math.max(0.0, Math.min(1.0, secondesRestantes / (double) DUREE_COMPTE_A_REBOURS)));
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> tick(joueurs, secondesRestantes - 1, auLancement), 20L);
    }

    private void liberer(List<Player> joueurs) {
        for (Player joueur : joueurs) {
            geles.remove(joueur.getUniqueId());
            if (joueur.isOnline()) {
                Titre.immediat(joueur, Messages.get("freezemanager.go"), Messages.get("freezemanager.puisse_le_sort_vous_etre_favorable"), 5, 30, 10);
            }
        }

        if (barreCompteARebours != null) {
            barreCompteARebours.removeAll();
            barreCompteARebours = null;
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!geles.contains(event.getPlayer().getUniqueId())) {
            return;
        }

        if (event.getTo() == null) {
            return;
        }

        double dx = event.getTo().getX() - event.getFrom().getX();
        double dy = event.getTo().getY() - event.getFrom().getY();
        double dz = event.getTo().getZ() - event.getFrom().getZ();

        if (dx != 0 || dy != 0 || dz != 0) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler
    public void onClicConteneur(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!estGele(event.getPlayer())) return;
        if (event.getClickedBlock() == null) return;
        if (!(event.getClickedBlock().getState() instanceof Container)) return;

        event.setCancelled(true);
        event.getPlayer().sendActionBar(Messages.get("commun.en_attente_du_debut_de_la"));
    }
}
